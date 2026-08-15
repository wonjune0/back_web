package com.wonjune.backweb.stock;

import com.wonjune.backweb.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 409 rather than 400: the request was well formed, it just lost the race for the last
 * units. Carries the product and what is left so the caller can name it in the message.
 */
@Getter
public class OutOfStockException extends ApiException {

	private final Long productId;
	private final int remaining;

	public OutOfStockException(Long productId, int remaining) {
		super(HttpStatus.CONFLICT, "재고가 부족합니다 (남은 수량 " + remaining + "개)");
		this.productId = productId;
		this.remaining = remaining;
	}

}
