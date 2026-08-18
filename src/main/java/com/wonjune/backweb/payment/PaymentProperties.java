package com.wonjune.backweb.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment")
@Getter
@Setter
public class PaymentProperties {

	/** Credential for the payment provider. Supplied from Secrets Manager in prod. */
	private String secretKey;

	private final Mock mock = new Mock();

	@Getter
	@Setter
	public static class Mock {

		private int minLatencyMs = 200;

		private int maxLatencyMs = 800;

		/** Share of calls that come back declined. Kept at 0 outside local runs. */
		private double failureRate = 0.0;

		/**
		 * When false, the X-Force-Payment-Failure request header is ignored. Left off in
		 * prod so nobody can steer another user's checkout into the failure branch.
		 */
		private boolean allowForcedFailure = false;

	}

}
