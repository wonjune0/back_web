package com.wonjune.backweb.order.dto;

public record OrderItemDto(
		Long productId,
		String productName,
		String imageUrl,
		Long priceSnapshot,
		Integer quantity,
		Long subtotal
) {
}
