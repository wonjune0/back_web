package com.wonjune.backweb.product.dto;

public record ProductDetailDto(
		Long id,
		String parentCategory,
		String category,
		String name,
		String imageUrl,
		Long originalPrice,
		Long price,
		String deliveryBadge,
		String deliveryText,
		Double rating,
		Integer reviewCount,
		Long rewardAmount,
		String detailDescription
) {
}
