package com.wonjune.backweb.product;

import com.wonjune.backweb.category.Category;
import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.product.dto.ProductDetailDto;
import com.wonjune.backweb.product.dto.ProductSearchCriteria;
import com.wonjune.backweb.product.dto.ProductSummaryDto;
import com.wonjune.backweb.stock.StockService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final StockService stockService;

	public ProductService(ProductRepository productRepository, StockService stockService) {
		this.productRepository = productRepository;
		this.stockService = stockService;
	}

	public PageResponse<ProductSummaryDto> search(ProductSearchCriteria criteria) {
		String search = StringUtils.hasText(criteria.search()) ? criteria.search().trim() : null;

		String category = null;
		String parentCategory = null;
		if (search == null) {
			if (StringUtils.hasText(criteria.category())) {
				category = criteria.category();
			} else if (StringUtils.hasText(criteria.parentCategory())) {
				parentCategory = criteria.parentCategory();
			}
		}

		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), resolveSort(criteria.sort()));
		Page<Product> page = productRepository.search(search, category, parentCategory, pageRequest);

		// One stock lookup for the whole page rather than one per card.
		Map<Long, Integer> stockByProductId = stockService.quantitiesOf(
				page.getContent().stream().map(Product::getId).toList());

		return PageResponse.from(page.map(product ->
				toSummaryDto(product, stockByProductId.getOrDefault(product.getId(), 0))));
	}

	public ProductDetailDto getDetail(Long id) {
		Product product = productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"));
		Category category = product.getCategory();
		int stockQuantity = stockService.quantitiesOf(List.of(id)).getOrDefault(id, 0);

		return new ProductDetailDto(
				product.getId(),
				parentCategoryName(category),
				category.getName(),
				product.getName(),
				product.getImageUrl(),
				product.getOriginalPrice(),
				product.getPrice(),
				product.getDeliveryBadge(),
				product.getDeliveryText(),
				product.getRating().doubleValue(),
				product.getReviewCount(),
				product.getRewardAmount(),
				product.getDetailDescription(),
				stockQuantity
		);
	}

	private ProductSummaryDto toSummaryDto(Product product, Integer stockQuantity) {
		Category category = product.getCategory();
		return new ProductSummaryDto(
				product.getId(),
				parentCategoryName(category),
				category.getName(),
				product.getName(),
				product.getImageUrl(),
				product.getOriginalPrice(),
				product.getPrice(),
				product.getDeliveryBadge(),
				product.getDeliveryText(),
				product.getRating().doubleValue(),
				product.getReviewCount(),
				product.getRewardAmount(),
				stockQuantity
		);
	}

	@Nullable
	private String parentCategoryName(Category leaf) {
		return leaf.getParent() != null ? leaf.getParent().getName() : null;
	}

	private Sort resolveSort(String sort) {
		if (sort == null) {
			return Sort.by(Sort.Direction.ASC, "id");
		}
		return switch (sort) {
			case "price-asc" -> Sort.by(Sort.Direction.ASC, "price");
			case "price-desc" -> Sort.by(Sort.Direction.DESC, "price");
			case "reviews" -> Sort.by(Sort.Direction.DESC, "reviewCount");
			default -> Sort.by(Sort.Direction.ASC, "id");
		};
	}

}
