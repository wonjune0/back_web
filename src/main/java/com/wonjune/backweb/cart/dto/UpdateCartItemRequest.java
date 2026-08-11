package com.wonjune.backweb.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(

		@NotNull(message = "quantity는 필수입니다")
		@Min(value = 1, message = "수량은 1 이상이어야 합니다")
		Integer quantity

) {
}
