package com.wonjune.backweb.order;

import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.order.dto.OrderDetailDto;
import com.wonjune.backweb.order.dto.OrderItemDto;
import com.wonjune.backweb.order.dto.OrderSummaryDto;
import com.wonjune.backweb.payment.Payment;
import com.wonjune.backweb.payment.PaymentRepository;
import com.wonjune.backweb.payment.PaymentStatus;
import com.wonjune.backweb.product.Product;
import com.wonjune.backweb.product.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the order API, split out so OrderService can reuse it after a checkout
 * without calling itself: a self-invocation goes straight to the object and never touches
 * the transactional proxy, which is the same trap CheckoutTransactions exists to avoid.
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final PaymentRepository paymentRepository;

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

		List<OrderItemDto> itemDtos = items.stream()
				.map(item -> {
					// The product may have been deleted since; the snapshot still describes the order.
					Product product = productsById.get(item.getProductId());
					String imageUrl = product != null ? product.getImageUrl() : null;
					return new OrderItemDto(item.getProductId(), item.getProductNameSnapshot(), imageUrl,
							item.getProductPriceSnapshot(), item.getQuantity(), item.getSubtotal());
				})
				.toList();

		Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
		PaymentStatus paymentStatus = payment != null ? payment.getStatus() : null;
		String pgTransactionId = payment != null ? payment.getPgTransactionId() : null;

		return new OrderDetailDto(
				order.getOrderNumber(), order.getStatus(), order.getPlacedAt(), itemDtos, order.getTotalPrice(),
				order.getRecipientName(), order.getRecipientPhone(), order.getZipcode(), order.getAddress1(),
				order.getAddress2(), order.getDeliveryRequest(), order.getPaymentMethod(),
				paymentStatus, pgTransactionId);
	}

}
