package com.wonjune.backweb.product.dto;

public record ProductSearchCriteria(
		String search,
		String parentCategory,
		String category,
		String sort,
		int page,
		int size
) {
}
