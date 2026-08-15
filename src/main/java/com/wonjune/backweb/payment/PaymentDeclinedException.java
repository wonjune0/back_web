package com.wonjune.backweb.payment;

import com.wonjune.backweb.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 402: the request was valid and the order exists, the money just did not move. The order
 * is left FAILED and its stock has already been put back by the time this is thrown.
 */
public class PaymentDeclinedException extends ApiException {

	public PaymentDeclinedException(String reason) {
		super(HttpStatus.PAYMENT_REQUIRED, reason);
	}

}
