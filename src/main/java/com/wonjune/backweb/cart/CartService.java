package com.wonjune.backweb.cart;

import com.wonjune.backweb.cart.dto.CartItemResponse;
import com.wonjune.backweb.cart.dto.CartResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.product.Product;
import com.wonjune.backweb.product.ProductRepository;
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
		cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
				.ifPresentOrElse(
						item -> item.increaseQuantity(quantity),
						() -> cartItemRepository.save(new CartItem(cart.getId(), productId, quantity))
				);

		return buildResponse(cartItemRepository.findByCartId(cart.getId()));
	}

	@Transactional
	public CartResponse updateQuantity(Long userId, Long productId, int quantity) {
		Cart cart = requireCart(userId);
		CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "장바구니에 없는 상품입니다"));
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

		List<CartItemResponse> itemResponses = items.stream()
				.map(item -> toItemResponse(item, productsById.get(item.getProductId())))
				.toList();

		int totalQuantity = itemResponses.stream().mapToInt(CartItemResponse::quantity).sum();
		long totalPrice = itemResponses.stream().mapToLong(CartItemResponse::lineTotal).sum();
		return new CartResponse(itemResponses, totalQuantity, totalPrice);
	}

	private CartItemResponse toItemResponse(CartItem item, Product product) {
		long lineTotal = product.getPrice() * item.getQuantity();
		return new CartItemResponse(product.getId(), product.getName(), product.getImageUrl(),
				product.getPrice(), item.getQuantity(), lineTotal);
	}

}
