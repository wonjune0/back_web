package com.wonjune.backweb.cart;

import com.wonjune.backweb.cart.dto.CartItemResponse;
import com.wonjune.backweb.cart.dto.CartResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.product.Product;
import com.wonjune.backweb.product.ProductRepository;
import com.wonjune.backweb.stock.StockService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;

	@Transactional(readOnly = true)
	public CartResponse getCart(Long userId) {
		return cartRepository.findByUserId(userId)
				.map(cart -> buildResponse(cartItemRepository.findByCartId(cart.getId())))
				.orElseGet(CartResponse::empty);
	}

	@Transactional
	public CartResponse addItem(Long userId, Long productId, Integer requestedQuantity) {
		if (!productRepository.existsById(productId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다");
		}
		int quantity = requestedQuantity == null ? 1 : requestedQuantity;

		Cart cart = getOrCreateCart(userId);
		CartItem existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
		int existingQuantity = existing == null ? 0 : existing.getQuantity();
		requireStock(productId, existingQuantity + quantity);

		if (existing == null) {
			cartItemRepository.save(new CartItem(cart.getId(), productId, quantity));
		} else {
			existing.increaseQuantity(quantity);
		}

		return buildResponse(cartItemRepository.findByCartId(cart.getId()));
	}

	@Transactional
	public CartResponse updateQuantity(Long userId, Long productId, int quantity) {
		Cart cart = requireCart(userId);
		CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "장바구니에 없는 상품입니다"));
		requireStock(productId, quantity);
		item.changeQuantity(quantity);
		return buildResponse(cartItemRepository.findByCartId(cart.getId()));
	}

	@Transactional
	public CartResponse removeItem(Long userId, Long productId) {
		Cart cart = requireCart(userId);
		cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
		return buildResponse(cartItemRepository.findByCartId(cart.getId()));
	}

	@Transactional
	public void clear(Long userId) {
		cartRepository.findByUserId(userId)
				.ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
	}

	/**
	 * Advisory only -- stock is not held by putting something in a basket, so this just
	 * stops an obviously impossible quantity early. The binding check is the decrement at
	 * checkout, which is the one that runs under a lock.
	 */
	private void requireStock(Long productId, int requestedQuantity) {
		int available = stockService.quantitiesOf(List.of(productId)).getOrDefault(productId, 0);
		if (requestedQuantity > available) {
			throw new ApiException(HttpStatus.CONFLICT, "재고가 부족합니다 (남은 수량 " + available + "개)");
		}
	}

	private Cart getOrCreateCart(Long userId) {
		return cartRepository.findByUserId(userId)
				.orElseGet(() -> cartRepository.save(new Cart(userId)));
	}

	private Cart requireCart(Long userId) {
		return cartRepository.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "장바구니가 비어 있습니다"));
	}

	private CartResponse buildResponse(List<CartItem> items) {
		if (items.isEmpty()) {
			return CartResponse.empty();
		}

		List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
		Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		Map<Long, Integer> stockByProductId = stockService.quantitiesOf(productIds);

		List<CartItemResponse> itemResponses = items.stream()
				.map(item -> toItemResponse(item, productsById.get(item.getProductId()),
						stockByProductId.getOrDefault(item.getProductId(), 0)))
				.toList();

		int totalQuantity = itemResponses.stream().mapToInt(CartItemResponse::quantity).sum();
		long totalPrice = itemResponses.stream().mapToLong(CartItemResponse::lineTotal).sum();
		return new CartResponse(itemResponses, totalQuantity, totalPrice);
	}

	private CartItemResponse toItemResponse(CartItem item, Product product, Integer stockQuantity) {
		long lineTotal = product.getPrice() * item.getQuantity();
		return new CartItemResponse(product.getId(), product.getName(), product.getImageUrl(),
				product.getOriginalPrice(), product.getPrice(), product.getDeliveryBadge(),
				product.getDeliveryText(), product.getRating().doubleValue(), product.getReviewCount(),
				item.getQuantity(), lineTotal, stockQuantity);
	}

}
