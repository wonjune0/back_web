package com.wonjune.backweb.order;

import jakarta.persistence.AccessType;
import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * product_name_snapshot/product_price_snapshot preserve order-history integrity
 * even if the catalog price changes later or the product row itself is removed
 * (product_id is nullable, FK is ON DELETE SET NULL).
 */
@Entity
@Table(name = "order_items")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "product_name_snapshot", nullable = false)
	private String productNameSnapshot;

	@Column(name = "product_price_snapshot", nullable = false)
	private Long productPriceSnapshot;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false)
	private Long subtotal;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public OrderItem(Long orderId, Long productId, String productNameSnapshot,
			Long productPriceSnapshot, Integer quantity) {
		this.orderId = orderId;
		this.productId = productId;
		this.productNameSnapshot = productNameSnapshot;
		this.productPriceSnapshot = productPriceSnapshot;
		this.quantity = quantity;
		this.subtotal = productPriceSnapshot * quantity;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

}
