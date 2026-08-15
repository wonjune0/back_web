package com.wonjune.backweb.order.dto;

import java.time.LocalDateTime;

public record OrderSummaryDto(
		String orderNumber,
		LocalDateTime placedAt,
		String status,
		Long totalPrice,
		long itemCount,
		String firstProductName
) {
}
