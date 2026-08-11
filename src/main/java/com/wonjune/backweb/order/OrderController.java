package com.wonjune.backweb.order;

import com.wonjune.backweb.common.dto.PageResponse;
import com.wonjune.backweb.common.security.AuthenticatedUser;
import com.wonjune.backweb.order.dto.CreateOrderRequest;
import com.wonjune.backweb.order.dto.OrderDetailDto;
import com.wonjune.backweb.order.dto.OrderSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private static final int MAX_PAGE_SIZE = 50;

	private final OrderService orderService;

	@PostMapping
	public ResponseEntity<OrderDetailDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreateOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(principal.id(), request));
	}

	@GetMapping
	public PageResponse<OrderSummaryDto> list(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return orderService.listOrders(principal.id(), Math.max(page, 0), cappedSize);
	}

	@GetMapping("/{orderNumber}")
	public OrderDetailDto detail(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String orderNumber) {
		return orderService.getOrder(principal.id(), orderNumber);
	}

}
