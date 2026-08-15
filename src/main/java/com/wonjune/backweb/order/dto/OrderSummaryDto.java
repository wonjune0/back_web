package com.wonjune.backweb.order.dto;

import com.wonjune.backweb.order.OrderStatus;
import java.time.LocalDateTime;

public record OrderSummaryDto(
		String orderNumber,
		LocalDateTime placedAt,
		OrderStatus status,
		Long totalPrice,
		long itemCount,
		String firstProductName
) {
}
