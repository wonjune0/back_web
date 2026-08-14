package com.wonjune.backweb.category.dto;

import java.util.List;

/**
 * One top-level category and its leaves, shaped for the storefront's category sidebar.
 * The names are the same strings /api/products accepts as parentCategory and category.
 */
public record CategoryTreeDto(String name, List<String> subcategories) {
}
