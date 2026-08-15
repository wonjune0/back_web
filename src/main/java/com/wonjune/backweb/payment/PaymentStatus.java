package com.wonjune.backweb.payment;

public enum PaymentStatus {

	/** Row exists, gateway call has not returned yet. Claims the idempotency key. */
	PENDING,

	APPROVED,

	FAILED

}
