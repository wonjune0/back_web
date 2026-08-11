package com.wonjune.backweb.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(

		@NotBlank(message = "받는사람 이름은 필수입니다")
		String recipientName,

		@NotBlank(message = "연락처는 필수입니다")
		@Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰번호 형식이 올바르지 않습니다")
		String recipientPhone,

		@NotBlank(message = "우편번호는 필수입니다")
		String zipcode,

		@NotBlank(message = "주소는 필수입니다")
		String address1,

		@NotBlank(message = "상세주소는 필수입니다")
		String address2,

		@NotBlank(message = "배송 요청사항은 필수입니다")
		String deliveryRequest,

		@NotBlank(message = "결제수단은 필수입니다")
		String paymentMethod

) {
}
