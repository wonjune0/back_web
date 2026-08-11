package com.wonjune.backweb.cart.dto;

public record CartItemResponse(
		Long productId,
		String name,
		String imageUrl,
		Long price,
		Integer quantity,
		Long lineTotal
) {
}
