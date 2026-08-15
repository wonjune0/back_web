package com.wonjune.backweb.order;

import com.wonjune.backweb.cart.Cart;
import com.wonjune.backweb.cart.CartItem;
import com.wonjune.backweb.cart.CartItemRepository;
import com.wonjune.backweb.cart.CartRepository;
import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.order.dto.CreateOrderRequest;
import com.wonjune.backweb.order.dto.OrderDetailDto;
import com.wonjune.backweb.order.dto.OrderItemDto;
import com.wonjune.backweb.order.dto.OrderSummaryDto;
import com.wonjune.backweb.product.Product;
import com.wonjune.backweb.product.ProductRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

	private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final int ORDER_NUMBER_MAX_ATTEMPTS = 5;

	private static final Set<String> VALID_DELIVERY_REQUESTS = Set.of(
			"문 앞에 놓아주세요", "직접 받을게요", "경비실에 맡겨주세요", "배송 전 연락해주세요");
	private static final Set<String> VALID_PAYMENT_METHODS = Set.of("card", "transfer");

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;

	@Transactional
	public OrderDetailDto createOrder(Long userId, CreateOrderRequest request) {
		if (!VALID_DELIVERY_REQUESTS.contains(request.deliveryRequest())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 배송 요청사항입니다");
		}
		if (!VALID_PAYMENT_METHODS.contains(request.paymentMethod())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 결제수단입니다");
		}

		Cart cart = cartRepository.findByUserId(userId).orElse(null);
		List<CartItem> cartItems = cart == null ? List.of() : cartItemRepository.findByCartId(cart.getId());
		if (cartItems.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다");
		}
		cartItems = selectItems(cartItems, request.productIds());

		Map<Long, Product> productsById = productRepository
				.findAllById(cartItems.stream().map(CartItem::getProductId).toList()).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		long totalPrice = cartItems.stream()
				.mapToLong(item -> productsById.get(item.getProductId()).getPrice() * item.getQuantity())
				.sum();

		Order order = orderRepository.save(new Order(
				generateOrderNumber(), userId, request.recipientName(), request.recipientPhone(),
				request.zipcode(), request.address1(), request.address2(), request.deliveryRequest(),
				request.paymentMethod(), totalPrice));

		List<OrderItem> orderItems = cartItems.stream()
				.map(item -> {
					Product product = productsById.get(item.getProductId());
					return new OrderItem(order.getId(), product.getId(), product.getName(),
							product.getPrice(), item.getQuantity());
				})
				.toList();
		orderItemRepository.saveAll(orderItems);

		// Only the ordered rows leave the cart; anything the buyer left unticked stays.
		cartItemRepository.deleteByCartIdAndProductIdIn(cart.getId(),
				cartItems.stream().map(CartItem::getProductId).toList());

		return toDetailDto(order, orderItems, productsById);
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

	@Transactional(readOnly = true)
	public PageResponse<OrderSummaryDto> listOrders(Long userId, int page, int size) {
		Page<Order> orders = orderRepository.findByUserIdOrderByPlacedAtDesc(userId, PageRequest.of(page, size));

		// 주문마다 항목을 세면 페이지 크기만큼 쿼리가 붙는다. 한 번에 읽어 주문별로 묶는다.
		List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
		Map<Long, List<OrderItem>> itemsByOrderId = orderIds.isEmpty()
				? Map.of()
				: orderItemRepository.findByOrderIdIn(orderIds).stream()
						.collect(Collectors.groupingBy(OrderItem::getOrderId));

		return PageResponse.from(orders.map(order -> {
			List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
			String firstProductName = items.isEmpty() ? null : items.get(0).getProductNameSnapshot();
			return new OrderSummaryDto(order.getOrderNumber(), order.getPlacedAt(), order.getStatus(),
					order.getTotalPrice(), items.size(), firstProductName);
		}));
	}

	@Transactional(readOnly = true)
	public OrderDetailDto getOrder(Long userId, String orderNumber) {
		Order order = orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"));
		List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

		List<Long> productIds = items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).toList();
		Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		return toDetailDto(order, items, productsById);
	}

	private OrderDetailDto toDetailDto(Order order, List<OrderItem> items, Map<Long, Product> productsById) {
		List<OrderItemDto> itemDtos = items.stream()
				.map(item -> {
					Product product = productsById.get(item.getProductId());
					String imageUrl = product != null ? product.getImageUrl() : null;
					return new OrderItemDto(item.getProductId(), item.getProductNameSnapshot(), imageUrl,
							item.getProductPriceSnapshot(), item.getQuantity(), item.getSubtotal());
				})
				.toList();

		return new OrderDetailDto(
				order.getOrderNumber(), order.getStatus(), order.getPlacedAt(), itemDtos, order.getTotalPrice(),
				order.getRecipientName(), order.getRecipientPhone(), order.getZipcode(), order.getAddress1(),
				order.getAddress2(), order.getDeliveryRequest(), order.getPaymentMethod());
	}

	private String generateOrderNumber() {
		String datePart = LocalDate.now().format(ORDER_NUMBER_DATE_FORMAT);
		for (int attempt = 0; attempt < ORDER_NUMBER_MAX_ATTEMPTS; attempt++) {
			String candidate = datePart + "-" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
			if (!orderRepository.existsByOrderNumber(candidate)) {
				return candidate;
			}
		}
		throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "주문번호 생성에 실패했습니다");
	}

}
