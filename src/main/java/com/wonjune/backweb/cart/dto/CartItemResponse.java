package com.wonjune.backweb.cart.dto;

/**
 * Carries the same display fields as ProductSummaryDto because the cart page renders a
 * full product row -- discount rate, rating and delivery badge included -- and would
 * otherwise have to fetch every product again one by one.
 */
public record CartItemResponse(
		Long productId,
		String name,
		String imageUrl,
		Long originalPrice,
		Long price,
		String deliveryBadge,
		String deliveryText,
		Double rating,
		Integer reviewCount,
		Integer quantity,
		Long lineTotal
) {
}
