package com.wonjune.backweb.product;

import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.product.dto.ProductDetailDto;
import com.wonjune.backweb.product.dto.ProductSearchCriteria;
import com.wonjune.backweb.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ProductService productService;

	@GetMapping
	public PageResponse<ProductSummaryDto> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String parentCategory,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "recommended") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		ProductSearchCriteria criteria =
				new ProductSearchCriteria(search, parentCategory, category, sort, Math.max(page, 0), cappedSize);
		return productService.search(criteria);
	}

	@GetMapping("/{id}")
	public ProductDetailDto detail(@PathVariable Long id) {
		return productService.getDetail(id);
	}

}
