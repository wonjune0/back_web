package com.wonjune.backweb.order;

/**
 * PENDING -> PAID          gateway approved
 * PENDING -> FAILED        gateway declined, stock restored
 * PAID    -> CANCELLED     buyer cancelled, stock restored
 *
 * An order is created PENDING and is only ever completed by the second transaction, so a
 * crash between the two leaves it PENDING rather than silently paid.
 */
public enum OrderStatus {

	PENDING,
	PAID,
	FAILED,
	CANCELLED

}
