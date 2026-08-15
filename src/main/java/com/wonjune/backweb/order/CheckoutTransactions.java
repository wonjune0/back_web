package com.wonjune.backweb.order;

import com.wonjune.backweb.cart.Cart;
import com.wonjune.backweb.cart.CartItem;
import com.wonjune.backweb.cart.CartItemRepository;
import com.wonjune.backweb.cart.CartRepository;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.order.dto.CreateOrderRequest;
import com.wonjune.backweb.payment.Payment;
import com.wonjune.backweb.payment.PaymentRepository;
import com.wonjune.backweb.payment.PaymentResult;
import com.wonjune.backweb.product.Product;
import com.wonjune.backweb.product.ProductRepository;
import com.wonjune.backweb.stock.OutOfStockException;
import com.wonjune.backweb.stock.StockService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two database transactions a checkout is made of, deliberately kept apart from the
 * gateway call that runs between them.
 *
 * Wrapping the whole checkout in one transaction would hold a pooled connection and the
 * stock row locks for the entire duration of a remote call -- several hundred milliseconds
 * during which nothing else can touch those products, and one connection fewer for every
 * unrelated request. Under load that is how a slow payment provider takes the catalogue
 * down with it.
 *
 * This lives in its own bean because Spring's transaction advice is a proxy: calling these
 * from a method on the same object would bypass it and quietly run without a transaction.
 */
@Component
@RequiredArgsConstructor
public class CheckoutTransactions {

	private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final PaymentRepository paymentRepository;
	private final StockService stockService;

	/**
	 * T1. Prices the selected cart lines, takes their stock, and writes the order plus a
	 * PENDING payment row. Everything here rolls back together, so an order can never
	 * exist without its stock having been reserved.
	 *
	 * The payment insert is flushed rather than left to commit so a duplicate idempotency
	 * key fails here, where the caller can still tell what happened.
	 */
	@Transactional
	public Reservation reserve(Long userId, String idempotencyKey, CreateOrderRequest request) {
		Cart cart = cartRepository.findByUserId(userId).orElse(null);
		List<CartItem> cartItems = cart == null ? List.of() : cartItemRepository.findByCartId(cart.getId());
		if (cartItems.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다");
		}
		cartItems = selectItems(cartItems, request.productIds());

		Map<Long, Product> productsById = productRepository
				.findAllById(cartItems.stream().map(CartItem::getProductId).toList()).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		Map<Long, Integer> quantityByProductId = cartItems.stream()
				.collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity, Integer::sum));
		try {
			stockService.decrease(quantityByProductId);
		} catch (OutOfStockException e) {
			throw namedOutOfStock(e, productsById);
		}

		long totalPrice = cartItems.stream()
				.mapToLong(item -> productsById.get(item.getProductId()).getPrice() * item.getQuantity())
				.sum();

		Order order = orderRepository.save(new Order(
				generateOrderNumber(), userId, request.recipientName(), request.recipientPhone(),
				request.zipcode(), request.address1(), request.address2(), request.deliveryRequest(),
				request.paymentMethod(), totalPrice));

		orderItemRepository.saveAll(cartItems.stream()
				.map(item -> {
					Product product = productsById.get(item.getProductId());
					return new OrderItem(order.getId(), product.getId(), product.getName(),
							product.getPrice(), item.getQuantity());
				})
				.toList());

		paymentRepository.saveAndFlush(new Payment(order.getId(), idempotencyKey, totalPrice));

		return new Reservation(order.getId(), order.getOrderNumber(), totalPrice);
	}

	/**
	 * T2. Records what the gateway said and moves the order out of PENDING.
	 *
	 * A decline is returned rather than thrown: throwing here would roll back the very
	 * writes that record the failure and put the stock back, leaving the order stuck as
	 * PENDING with its stock still held. The caller raises the error after this commits.
	 */
	@Transactional
	public Settlement settle(Long orderId, PaymentResult result) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"));
		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다"));
		List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

		if (!result.approved()) {
			order.markFailed();
			payment.fail(result.failureReason());
			stockService.restore(quantitiesOf(items));
			// The cart is left alone so the buyer can simply try again.
			return new Settlement(false, result.failureReason(), null);
		}

		order.markPaid();
		payment.approve(result.pgTransactionId());
		cartRepository.findByUserId(order.getUserId()).ifPresent(cart ->
				cartItemRepository.deleteByCartIdAndProductIdIn(cart.getId(),
						items.stream().map(OrderItem::getProductId).toList()));

		return new Settlement(true, null, order.getOrderNumber());
	}

	/**
	 * Cancels a paid order and returns its stock to the shelf.
	 */
	@Transactional
	public void cancel(Long userId, String orderNumber) {
		Order order = orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"));
		try {
			order.cancel();
		} catch (IllegalStateException e) {
			throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
		}
		stockService.restore(quantitiesOf(orderItemRepository.findByOrderId(order.getId())));
	}

	private Map<Long, Integer> quantitiesOf(List<OrderItem> items) {
		return items.stream()
				.filter(item -> item.getProductId() != null)
				.collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
	}

	/**
	 * Narrows the cart to the requested products, preserving cart order. A null or empty
	 * selection means the whole cart. Ids that are not in the cart are rejected rather than
	 * ignored, so a stale checkout page cannot quietly place a smaller order than it showed.
	 */
	private List<CartItem> selectItems(List<CartItem> cartItems, List<Long> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return cartItems;
		}

		Set<Long> requested = Set.copyOf(productIds);
		List<CartItem> selected = cartItems.stream()
				.filter(item -> requested.contains(item.getProductId()))
				.toList();

		if (selected.size() != requested.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "장바구니에 없는 상품이 포함되어 있습니다");
		}
		return selected;
	}

	/**
	 * StockService only knows product ids, but the buyer needs to be told which item ran out.
	 */
	private ApiException namedOutOfStock(OutOfStockException e, Map<Long, Product> productsById) {
		Product product = productsById.get(e.getProductId());
		if (product == null) {
			return e;
		}
		return new ApiException(HttpStatus.CONFLICT,
				product.getName() + "의 " + e.getMessage());
	}

	/**
	 * No pre-check for an existing number: reading first and inserting afterwards leaves a
	 * window two same-day orders can pick the same value in. The unique index decides, and
	 * OrderService retries the whole reservation if it loses.
	 */
	private String generateOrderNumber() {
		return LocalDate.now().format(ORDER_NUMBER_DATE_FORMAT) + "-"
				+ ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
	}

	public record Reservation(Long orderId, String orderNumber, long totalPrice) {
	}

	public record Settlement(boolean approved, String failureReason, String orderNumber) {
	}

}
