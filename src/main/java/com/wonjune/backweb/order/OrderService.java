package com.wonjune.backweb.order;

import com.wonjune.backweb.common.exception.ApiException;
import com.wonjune.backweb.order.dto.CreateOrderRequest;
import com.wonjune.backweb.order.dto.OrderDetailDto;
import com.wonjune.backweb.payment.Payment;
import com.wonjune.backweb.payment.PaymentDeclinedException;
import com.wonjune.backweb.payment.PaymentGateway;
import com.wonjune.backweb.payment.PaymentRepository;
import com.wonjune.backweb.payment.PaymentResult;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sequences a checkout across two transactions and one remote call.
 *
 * Note what is missing: createOrder has no @Transactional. The gateway call in the middle
 * must run with no transaction open, so the boundaries live in CheckoutTransactions and
 * this class only decides the order things happen in.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private static final int RESERVE_MAX_ATTEMPTS = 3;

	private static final Set<String> VALID_DELIVERY_REQUESTS = Set.of(
			"문 앞에 놓아주세요", "직접 받을게요", "경비실에 맡겨주세요", "배송 전 연락해주세요");
	private static final Set<String> VALID_PAYMENT_METHODS = Set.of("card", "transfer");

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;
	private final CheckoutTransactions checkoutTransactions;
	private final OrderQueryService orderQueryService;

	public OrderDetailDto createOrder(Long userId, String idempotencyKey, boolean forceFailure,
			CreateOrderRequest request) {
		if (!VALID_DELIVERY_REQUESTS.contains(request.deliveryRequest())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 배송 요청사항입니다");
		}
		if (!VALID_PAYMENT_METHODS.contains(request.paymentMethod())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 결제수단입니다");
		}

		// A key we have already seen means this is a retry, not a second purchase.
		Optional<Payment> replay = paymentRepository.findByIdempotencyKey(idempotencyKey);
		if (replay.isPresent()) {
			return resolveReplay(userId, replay.get());
		}

		CheckoutTransactions.Reservation reservation = reserve(userId, idempotencyKey, request);
		if (reservation == null) {
			// A request running alongside this one claimed the key first.
			return resolveReplay(userId, paymentRepository.findByIdempotencyKey(idempotencyKey)
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "결제를 처리하고 있습니다")));
		}

		PaymentResult result = approve(idempotencyKey, reservation, forceFailure);
		CheckoutTransactions.Settlement settlement = checkoutTransactions.settle(reservation.orderId(), result);
		if (!settlement.approved()) {
			throw new PaymentDeclinedException(settlement.failureReason());
		}
		return orderQueryService.getOrder(userId, settlement.orderNumber());
	}

	public void cancelOrder(Long userId, String orderNumber) {
		checkoutTransactions.cancel(userId, orderNumber);
	}

	/**
	 * Returns null when the idempotency key was claimed by a concurrent request.
	 *
	 * Two different unique constraints can fail in here, so rather than parsing the driver's
	 * error text we ask which one it was: if the key is now taken, we lost the idempotency
	 * race; if it is not, the order number collided and a fresh attempt will pick another.
	 */
	private CheckoutTransactions.Reservation reserve(Long userId, String idempotencyKey,
			CreateOrderRequest request) {
		for (int attempt = 1; attempt <= RESERVE_MAX_ATTEMPTS; attempt++) {
			try {
				return checkoutTransactions.reserve(userId, idempotencyKey, request);
			} catch (DataIntegrityViolationException e) {
				if (paymentRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
					return null;
				}
				log.warn("Order number collided, retrying reservation (attempt {})", attempt);
				if (attempt == RESERVE_MAX_ATTEMPTS) {
					throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "주문번호 생성에 실패했습니다");
				}
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/**
	 * A gateway that throws is a gateway that declined, as far as the order is concerned.
	 * Letting the exception out instead would leave the order PENDING forever, holding
	 * stock nobody else can buy.
	 */
	private PaymentResult approve(String idempotencyKey, CheckoutTransactions.Reservation reservation,
			boolean forceFailure) {
		try {
			return paymentGateway.approve(idempotencyKey, reservation.totalPrice(), forceFailure);
		} catch (RuntimeException e) {
			log.error("Payment gateway call failed for order {}", reservation.orderNumber(), e);
			return PaymentResult.declined("결제 처리 중 오류가 발생했습니다");
		}
	}

	private OrderDetailDto resolveReplay(Long userId, Payment payment) {
		Order order = orderRepository.findById(payment.getOrderId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"));
		if (!order.getUserId().equals(userId)) {
			// Someone else's key. Say nothing about whose.
			throw new ApiException(HttpStatus.CONFLICT, "이미 사용된 요청입니다");
		}

		return switch (payment.getStatus()) {
			case APPROVED -> orderQueryService.getOrder(userId, order.getOrderNumber());
			case FAILED -> throw new PaymentDeclinedException(payment.getFailureReason());
			// The first request is still waiting on the gateway; there is nothing to return yet.
			case PENDING -> throw new ApiException(HttpStatus.CONFLICT, "결제를 처리하고 있습니다");
		};
	}

}
