package com.wonjune.backweb.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

		@NotBlank(message = "이메일은 필수입니다")
		@Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식이 올바르지 않습니다")
		String email,

		@NotBlank(message = "비밀번호는 필수입니다")
		@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
		String password,

		@NotBlank(message = "이름은 필수입니다")
		String name,

		@NotBlank(message = "휴대폰번호는 필수입니다")
		@Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰번호 형식이 올바르지 않습니다")
		String phone,

		boolean age14,
		boolean termsOfService,
		boolean financialTerms,
		boolean thirdPartyConsent

) {
}
