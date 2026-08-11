package com.wonjune.backweb.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_NAME = "name";

	private final JwtProperties jwtProperties;

	public String generateToken(Long userId, String email, String name) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(CLAIM_EMAIL, email)
				.claim(CLAIM_NAME, name)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key())
				.compact();
	}

	public AuthenticatedUser parseToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key())
				.build()
				.parseSignedClaims(token)
				.getPayload();

		return new AuthenticatedUser(
				Long.valueOf(claims.getSubject()),
				claims.get(CLAIM_EMAIL, String.class),
				claims.get(CLAIM_NAME, String.class)
		);
	}

	public long getExpirationMs() {
		return jwtProperties.getExpirationMs();
	}

	private SecretKey key() {
		return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}

}
