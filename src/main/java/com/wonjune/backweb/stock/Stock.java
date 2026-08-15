package com.wonjune.backweb.stock;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Rows are seeded by migration and only ever updated, so updated_at is left to the
 * column's ON UPDATE clause rather than being written from here.
 */
@Entity
@Table(name = "product_stocks")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

	@Id
	@Column(name = "product_id")
	private Long productId;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	/**
	 * Used by the read-modify-write strategies only. The atomic strategy never loads the
	 * entity -- it pushes the same check down into the UPDATE's WHERE clause.
	 */
	boolean decrease(int amount) {
		if (quantity < amount) {
			return false;
		}
		quantity -= amount;
		return true;
	}

	void increase(int amount) {
		quantity += amount;
	}

}
