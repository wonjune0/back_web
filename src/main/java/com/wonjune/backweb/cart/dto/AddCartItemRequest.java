package com.wonjune.backweb.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(

		@NotNull(message = "productId는 필수입니다")
		Long productId,

		@Min(value = 1, message = "수량은 1 이상이어야 합니다")
		Integer quantity

) {
}
