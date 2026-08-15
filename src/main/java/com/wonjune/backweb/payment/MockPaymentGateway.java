package com.wonjune.backweb.payment;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real PSP. It is not a shortcut around the payment step -- the order flow
 * still treats it as a slow, remote call that is allowed to fail, which is the property
 * the design has to cope with. Two things a live provider cannot give us during a demo or
 * a load test: a latency we control, and a failure we can trigger on cue.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

	private static final String[] DECLINE_REASONS = {
			"카드 한도를 초과했습니다", "카드사 승인이 거절되었습니다", "잔액이 부족합니다"
	};

	private final PaymentProperties paymentProperties;

	@Override
	public PaymentResult approve(String idempotencyKey, long amount, boolean forceFailure) {
		PaymentProperties.Mock config = paymentProperties.getMock();
		sleepLikeARemoteCall(config);

		boolean declined = (forceFailure && config.isAllowForcedFailure())
				|| ThreadLocalRandom.current().nextDouble() < config.getFailureRate();

		if (declined) {
			String reason = DECLINE_REASONS[ThreadLocalRandom.current().nextInt(DECLINE_REASONS.length)];
			log.info("Payment declined key={} amount={} reason={}", idempotencyKey, amount, reason);
			return PaymentResult.declined(reason);
		}

		String transactionId = "PG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		log.info("Payment approved key={} amount={} tx={}", idempotencyKey, amount, transactionId);
		return PaymentResult.approved(transactionId);
	}

	private void sleepLikeARemoteCall(PaymentProperties.Mock config) {
		int max = Math.max(config.getMinLatencyMs(), config.getMaxLatencyMs());
		if (max <= 0) {
			return;
		}
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(Math.max(config.getMinLatencyMs(), 0), max + 1));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
