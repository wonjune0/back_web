package com.wonjune.backweb.auth;

import com.wonjune.backweb.auth.dto.LoginRequest;
import com.wonjune.backweb.auth.dto.LoginResponse;
import com.wonjune.backweb.auth.dto.SignupRequest;
import com.wonjune.backweb.auth.dto.UserResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.common.security.JwtTokenProvider;
import com.wonjune.backweb.user.User;
import com.wonjune.backweb.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (!(request.age14() && request.termsOfService() && request.financialTerms()
				&& request.thirdPartyConsent())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해야 합니다");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다");
		}

		User user = User.builder()
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.name(request.name())
				.phone(request.phone())
				.agreeAge14(request.age14())
				.agreeTermsOfService(request.termsOfService())
				.agreeFinancialTerms(request.financialTerms())
				.agreeThirdPartyConsent(request.thirdPartyConsent())
				.build();

		User saved = userRepository.save(user);
		return new UserResponse(saved.getId(), saved.getEmail(), saved.getName());
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
		}

		String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName());
		UserResponse userResponse = new UserResponse(user.getId(), user.getEmail(), user.getName());
		return new LoginResponse(token, "Bearer", jwtTokenProvider.getExpirationMs() / 1000, userResponse);
	}

}
