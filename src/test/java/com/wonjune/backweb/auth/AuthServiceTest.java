package com.wonjune.backweb.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wonjune.backweb.auth.dto.LoginRequest;
import com.wonjune.backweb.auth.dto.LoginResponse;
import com.wonjune.backweb.auth.dto.SignupRequest;
import com.wonjune.backweb.auth.dto.UserResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.common.security.JwtTokenProvider;
import com.wonjune.backweb.user.User;
import com.wonjune.backweb.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@InjectMocks
	private AuthService authService;

	private SignupRequest fullyAgreedSignupRequest() {
		return new SignupRequest("user@example.com", "password123", "홍길동", "010-1234-5678",
				true, true, true, true);
	}

	@Test
	void signup_전체약관동의하면_가입성공() {
		SignupRequest request = fullyAgreedSignupRequest();
		when(userRepository.existsByEmail(request.email())).thenReturn(false);
		when(passwordEncoder.encode(request.password())).thenReturn("encoded");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User toSave = invocation.getArgument(0);
			return User.builder()
					.email(toSave.getEmail())
					.passwordHash(toSave.getPasswordHash())
					.name(toSave.getName())
					.phone(toSave.getPhone())
					.agreeAge14(true).agreeTermsOfService(true)
					.agreeFinancialTerms(true).agreeThirdPartyConsent(true)
					.build();
		});

		UserResponse response = authService.signup(request);

		assertThat(response.email()).isEqualTo(request.email());
		assertThat(response.name()).isEqualTo(request.name());
	}

	@Test
	void signup_약관중하나라도미동의면_400() {
		SignupRequest request = new SignupRequest("user@example.com", "password123", "홍길동",
				"010-1234-5678", true, true, true, false);

		assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("약관");
	}

	@Test
	void signup_이메일이미존재하면_409() {
		SignupRequest request = fullyAgreedSignupRequest();
		when(userRepository.existsByEmail(request.email())).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("이미 가입된");
	}

	@Test
	void login_비밀번호불일치면_401() {
		LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
		User existing = User.builder()
				.email(request.email()).passwordHash("encoded").name("홍길동").phone("010-1234-5678")
				.agreeAge14(true).agreeTermsOfService(true).agreeFinancialTerms(true)
				.agreeThirdPartyConsent(true).build();
		when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existing));
		when(passwordEncoder.matches(request.password(), existing.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("이메일 또는 비밀번호");
	}

	@Test
	void login_존재하지않는이메일이면_401() {
		LoginRequest request = new LoginRequest("nobody@example.com", "password123");
		when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("이메일 또는 비밀번호");
	}

	@Test
	void login_성공하면_토큰발급() {
		LoginRequest request = new LoginRequest("user@example.com", "password123");
		User existing = User.builder()
				.email(request.email()).passwordHash("encoded").name("홍길동").phone("010-1234-5678")
				.agreeAge14(true).agreeTermsOfService(true).agreeFinancialTerms(true)
				.agreeThirdPartyConsent(true).build();
		when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existing));
		when(passwordEncoder.matches(request.password(), existing.getPasswordHash())).thenReturn(true);
		when(jwtTokenProvider.generateToken(existing.getId(), existing.getEmail(), existing.getName()))
				.thenReturn("dummy-token");
		when(jwtTokenProvider.getExpirationMs()).thenReturn(7_200_000L);

		LoginResponse response = authService.login(request);

		assertThat(response.accessToken()).isEqualTo("dummy-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(7200L);
		assertThat(response.user().email()).isEqualTo(request.email());
	}

}
