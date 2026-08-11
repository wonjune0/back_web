package com.wonjune.backweb.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("test-only-secret-key-must-be-at-least-32-bytes-long");
		properties.setExpirationMs(7_200_000L);
		jwtTokenProvider = new JwtTokenProvider(properties);
	}

	@Test
	void 토큰생성후_파싱하면_동일한클레임을_되돌려준다() {
		String token = jwtTokenProvider.generateToken(42L, "user@example.com", "홍길동");

		AuthenticatedUser parsed = jwtTokenProvider.parseToken(token);

		assertThat(parsed.id()).isEqualTo(42L);
		assertThat(parsed.email()).isEqualTo("user@example.com");
		assertThat(parsed.name()).isEqualTo("홍길동");
	}

	@Test
	void 변조된토큰은_파싱시예외() {
		String token = jwtTokenProvider.generateToken(1L, "a@b.com", "abc");
		String tampered = token.substring(0, token.length() - 2) + "xx";

		assertThatThrownBy(() -> jwtTokenProvider.parseToken(tampered))
				.isInstanceOf(JwtException.class);
	}

}
