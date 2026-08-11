package com.wonjune.backweb.cart.dto;

import java.util.List;

public record CartResponse(List<CartItemResponse> items, int totalQuantity, long totalPrice) {

	public static CartResponse empty() {
		return new CartResponse(List.of(), 0, 0L);
	}

}
