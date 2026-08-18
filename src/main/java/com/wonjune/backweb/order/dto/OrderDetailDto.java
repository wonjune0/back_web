package com.wonjune.backweb.order.dto;

import com.wonjune.backweb.order.OrderStatus;
import com.wonjune.backweb.payment.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailDto(
		String orderNumber,
		OrderStatus status,
		LocalDateTime placedAt,
		List<OrderItemDto> items,
		Long totalPrice,
		String recipientName,
		String recipientPhone,
		String zipcode,
		String address1,
		String address2,
		String deliveryRequest,
		String paymentMethod,
		PaymentStatus paymentStatus,
		String pgTransactionId
) {
}
