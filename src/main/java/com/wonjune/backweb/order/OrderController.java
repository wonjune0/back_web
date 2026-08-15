package com.wonjune.backweb.order;

import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.common.security.AuthenticatedUser;
import com.wonjune.backweb.order.dto.CreateOrderRequest;
import com.wonjune.backweb.order.dto.OrderDetailDto;
import com.wonjune.backweb.order.dto.OrderSummaryDto;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private static final int MAX_PAGE_SIZE = 50;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

	private final OrderService orderService;
	private final OrderQueryService orderQueryService;

	/**
	 * Idempotency-Key is what makes a retried checkout safe. It is optional so that a
	 * client that does not send one still works -- it simply gets no protection, because a
	 * server-generated key is different on every attempt.
	 *
	 * X-Force-Payment-Failure only does anything where payment.mock.allow-forced-failure is
	 * on, which is never the case in prod.
	 */
	@PostMapping
	public ResponseEntity<OrderDetailDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestHeader(value = "X-Force-Payment-Failure", defaultValue = "false") boolean forceFailure,
			@Valid @RequestBody CreateOrderRequest request) {
		OrderDetailDto order = orderService.createOrder(
				principal.id(), normalizeKey(idempotencyKey), forceFailure, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(order);
	}

	@GetMapping
	public PageResponse<OrderSummaryDto> list(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return orderQueryService.listOrders(principal.id(), Math.max(page, 0), cappedSize);
	}

	@GetMapping("/{orderNumber}")
	public OrderDetailDto detail(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String orderNumber) {
		return orderQueryService.getOrder(principal.id(), orderNumber);
	}

	@PostMapping("/{orderNumber}/cancel")
	public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String orderNumber) {
		orderService.cancelOrder(principal.id(), orderNumber);
		return ResponseEntity.noContent().build();
	}

	/**
	 * The key is stored in a VARCHAR(64) unique column, so an oversized one has to be
	 * rejected before it reaches the insert rather than truncated into a collision.
	 */
	private String normalizeKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return UUID.randomUUID().toString();
		}
		String trimmed = idempotencyKey.trim();
		if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key가 너무 깁니다");
		}
		return trimmed;
	}

}
