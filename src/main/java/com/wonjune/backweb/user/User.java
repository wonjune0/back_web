package com.wonjune.backweb.user;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String phone;

	@Column(name = "agree_age14", nullable = false)
	private boolean agreeAge14;

	@Column(name = "agree_terms_of_service", nullable = false)
	private boolean agreeTermsOfService;

	@Column(name = "agree_financial_terms", nullable = false)
	private boolean agreeFinancialTerms;

	@Column(name = "agree_third_party_consent", nullable = false)
	private boolean agreeThirdPartyConsent;

	@Column(name = "terms_agreed_at", nullable = false)
	private LocalDateTime termsAgreedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public User(String email, String passwordHash, String name, String phone,
			boolean agreeAge14, boolean agreeTermsOfService,
			boolean agreeFinancialTerms, boolean agreeThirdPartyConsent) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
		this.phone = phone;
		this.agreeAge14 = agreeAge14;
		this.agreeTermsOfService = agreeTermsOfService;
		this.agreeFinancialTerms = agreeFinancialTerms;
		this.agreeThirdPartyConsent = agreeThirdPartyConsent;
		this.termsAgreedAt = LocalDateTime.now();
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
