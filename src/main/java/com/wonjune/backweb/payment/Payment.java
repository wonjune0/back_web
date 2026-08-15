package com.wonjune.backweb.payment;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	/**
	 * Client-supplied, unique across the table. Inserting this row is what claims the key:
	 * whichever concurrent request commits first owns the attempt, the rest bounce off the
	 * unique index.
	 */
	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "pg_transaction_id")
	private String pgTransactionId;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public Payment(Long orderId, String idempotencyKey, Long amount) {
		this.orderId = orderId;
		this.idempotencyKey = idempotencyKey;
		this.amount = amount;
		this.status = PaymentStatus.PENDING;
	}

	public void approve(String pgTransactionId) {
		this.status = PaymentStatus.APPROVED;
		this.pgTransactionId = pgTransactionId;
		this.completedAt = LocalDateTime.now();
	}

	public void fail(String failureReason) {
		this.status = PaymentStatus.FAILED;
		this.failureReason = failureReason;
		this.completedAt = LocalDateTime.now();
	}

	@PrePersist
	protected void onCreate() {
		this.requestedAt = LocalDateTime.now();
	}

}
