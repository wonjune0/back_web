package com.wonjune.backweb.cart;

import com.wonjune.backweb.cart.dto.AddCartItemRequest;
import com.wonjune.backweb.cart.dto.CartResponse;
import com.wonjune.backweb.cart.dto.UpdateCartItemRequest;
import com.wonjune.backweb.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	@GetMapping
	public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser principal) {
		return cartService.getCart(principal.id());
	}

	@PostMapping("/items")
	public CartResponse addItem(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody AddCartItemRequest request) {
		return cartService.addItem(principal.id(), request.productId(), request.quantity());
	}

	@PatchMapping("/items/{productId}")
	public CartResponse updateItem(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request) {
		return cartService.updateQuantity(principal.id(), productId, request.quantity());
	}

	@DeleteMapping("/items/{productId}")
	public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long productId) {
		return cartService.removeItem(principal.id(), productId);
	}

	@DeleteMapping
	public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthenticatedUser principal) {
		cartService.clear(principal.id());
		return ResponseEntity.noContent().build();
	}

}
