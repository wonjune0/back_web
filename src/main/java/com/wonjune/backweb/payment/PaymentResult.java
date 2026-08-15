package com.wonjune.backweb.payment;

public record PaymentResult(boolean approved, String pgTransactionId, String failureReason) {

	public static PaymentResult approved(String pgTransactionId) {
		return new PaymentResult(true, pgTransactionId, null);
	}

	public static PaymentResult declined(String failureReason) {
		return new PaymentResult(false, null, failureReason);
	}

}
