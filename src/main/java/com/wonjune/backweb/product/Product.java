package com.wonjune.backweb.product;

import com.wonjune.backweb.category.Category;
import jakarta.persistence.AccessType;
import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false)
	private String name;

	@Column(name = "image_url", nullable = false)
	private String imageUrl;

	@Column(name = "original_price")
	private Long originalPrice;

	@Column(nullable = false)
	private Long price;

	@Column(name = "delivery_badge", nullable = false)
	private String deliveryBadge;

	@Column(name = "delivery_text", nullable = false)
	private String deliveryText;

	@Column(nullable = false)
	private BigDecimal rating;

	@Column(name = "review_count", nullable = false)
	private Integer reviewCount;

	@Column(name = "reward_amount", nullable = false)
	private Long rewardAmount;

	@Column(name = "detail_description", nullable = false)
	private String detailDescription;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
