package com.wonjune.backweb.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailDto(
		String orderNumber,
		String status,
		LocalDateTime placedAt,
		List<OrderItemDto> items,
		Long totalPrice,
		String recipientName,
		String recipientPhone,
		String zipcode,
		String address1,
		String address2,
		String deliveryRequest,
		String paymentMethod
) {
}
