package com.wonjune.backweb.cart;

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

/**
 * cart_id/product_id are plain FK columns (no JPA association) -- CartService
 * resolves product display data via a single batched ProductRepository lookup
 * instead of navigating entity graphs.
 */
@Entity
@Table(name = "cart_items")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cart_id", nullable = false)
	private Long cartId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public CartItem(Long cartId, Long productId, Integer quantity) {
		this.cartId = cartId;
		this.productId = productId;
		this.quantity = quantity;
	}

	public void increaseQuantity(int delta) {
		this.quantity += delta;
	}

	public void changeQuantity(int quantity) {
		this.quantity = quantity;
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
