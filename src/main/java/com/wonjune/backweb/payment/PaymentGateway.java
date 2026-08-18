package com.wonjune.backweb.payment;

/**
 * The seam between our order flow and whoever actually moves the money. Everything the
 * application needs from a PSP is here, so swapping the mock for a real provider is one
 * new implementation of this interface and no change to OrderService.
 *
 * Implementations are called from outside any database transaction -- see
 * OrderService.createOrder.
 */
public interface PaymentGateway {

	/**
	 * @param idempotencyKey forwarded to the provider so a retried call is recognised on
	 *                       their side too, not just ours
	 * @param forceFailure   test hook for demonstrating the failure path; implementations
	 *                       must ignore it unless explicitly enabled by configuration
	 */
	PaymentResult approve(String idempotencyKey, long amount, boolean forceFailure);

}
