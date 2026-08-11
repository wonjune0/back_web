package com.wonjune.backweb.order;

import jakarta.persistence.AccessType;
import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, unique = true)
	private String orderNumber;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String status;

	@Column(name = "recipient_name", nullable = false)
	private String recipientName;

	@Column(name = "recipient_phone", nullable = false)
	private String recipientPhone;

	@Column(nullable = false)
	private String zipcode;

	@Column(nullable = false)
	private String address1;

	@Column
	private String address2;

	@Column(name = "delivery_request", nullable = false)
	private String deliveryRequest;

	@Column(name = "payment_method", nullable = false)
	private String paymentMethod;

	@Column(name = "total_price", nullable = false)
	private Long totalPrice;

	@Column(name = "placed_at", nullable = false)
	private LocalDateTime placedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public Order(String orderNumber, Long userId, String recipientName, String recipientPhone,
			String zipcode, String address1, String address2, String deliveryRequest,
			String paymentMethod, Long totalPrice) {
		this.orderNumber = orderNumber;
		this.userId = userId;
		this.status = "PLACED";
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.zipcode = zipcode;
		this.address1 = address1;
		this.address2 = address2;
		this.deliveryRequest = deliveryRequest;
		this.paymentMethod = paymentMethod;
		this.totalPrice = totalPrice;
		this.placedAt = LocalDateTime.now();
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

}
