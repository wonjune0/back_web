package com.wonjune.backweb.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wonjune.backweb.auth.dto.LoginRequest;
import com.wonjune.backweb.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIT {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("back_web")
			.withUsername("back_web")
			.withPassword("back_web");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		// The gateway's simulated latency is the point in production and dead weight here.
		registry.add("payment.mock.min-latency-ms", () -> 0);
		registry.add("payment.mock.max-latency-ms", () -> 0);
		registry.add("payment.mock.failure-rate", () -> 0.0);
		registry.add("payment.mock.allow-forced-failure", () -> true);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String loginAs(String email) throws Exception {
		SignupRequest signupRequest = new SignupRequest(
				email, "password123", "홍길동", "010-1234-5678", true, true, true, true);
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupRequest)));

		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asText();
	}

	private static final String CREATE_ORDER_BODY = """
			{
			  "recipientName": "홍길동",
			  "recipientPhone": "010-1234-5678",
			  "zipcode": "12345",
			  "address1": "서울시 강남구 테헤란로 1",
			  "address2": "101동 202호",
			  "deliveryRequest": "문 앞에 놓아주세요",
			  "paymentMethod": "card"
			}
			""";

	@Test
	void 장바구니로_주문생성하면_스냅샷가격으로_총액계산되고_장바구니가_비워진다() throws Exception {
		String token = loginAs("order-user1@example.com");

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":2}"));
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":7,\"quantity\":1}"));

		// product 1 price 15200 * 2 + product 7 price 18000 * 1 = 48400
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalPrice").value(48400))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
				.andExpect(jsonPath("$.pgTransactionId").isNotEmpty())
				.andExpect(jsonPath("$.orderNumber").isNotEmpty());

		mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void 장바구니가_비어있으면_주문생성_400() throws Exception {
		String token = loginAs("order-user2@example.com");

		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 유효하지않은_배송요청사항이면_400() throws Exception {
		String token = loginAs("order-user3@example.com");
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"));

		String invalidBody = CREATE_ORDER_BODY.replace("문 앞에 놓아주세요", "아무데나 놓아주세요");
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(invalidBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 주문목록과_상세조회() throws Exception {
		String token = loginAs("order-user4@example.com");
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"));
		String createBody = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andReturn().getResponse().getContentAsString();
		String orderNumber = objectMapper.readTree(createBody).get("orderNumber").asText();

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber))
				.andExpect(jsonPath("$.content[0].itemCount").value(1))
				.andExpect(jsonPath("$.content[0].firstProductName").value("수분 진정 크림 100ml, 1개"))
				.andExpect(jsonPath("$.content[0].totalPrice").value(15200));

		mockMvc.perform(get("/api/orders/" + orderNumber).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderNumber").value(orderNumber))
				.andExpect(jsonPath("$.items[0].productName").value("수분 진정 크림 100ml, 1개"));
	}

	@Test
	void productIds로_선택한_상품만_주문되고_나머지는_장바구니에_남는다() throws Exception {
		String token = loginAs("order-partial@example.com");

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":2}"));
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":7,\"quantity\":1}"));

		// only product 1 is ordered: 15200 * 2 = 30400, product 7 stays behind
		String selectedBody = CREATE_ORDER_BODY.replaceFirst("\\{", "{\n  \"productIds\": [1],");
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(selectedBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalPrice").value(30400))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(1));

		mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(7));
	}

	@Test
	void 장바구니에_없는_productId를_주문하면_400() throws Exception {
		String token = loginAs("order-notincart@example.com");
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"));

		String selectedBody = CREATE_ORDER_BODY.replaceFirst("\\{", "{\n  \"productIds\": [1, 2],");
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(selectedBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 주문이_없으면_빈_목록을_반환한다() throws Exception {
		String token = loginAs("order-empty-list@example.com");

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void 다른사용자의_주문은_조회할수없다() throws Exception {
		String ownerToken = loginAs("order-owner@example.com");
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"));
		String createBody = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + ownerToken)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andReturn().getResponse().getContentAsString();
		String orderNumber = objectMapper.readTree(createBody).get("orderNumber").asText();

		String otherToken = loginAs("order-other@example.com");
		mockMvc.perform(get("/api/orders/" + orderNumber).header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
	}

	private int stockOf(long productId) throws Exception {
		String body = mockMvc.perform(get("/api/products/" + productId))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("stockQuantity").asInt();
	}

	@Test
	void 주문이_완료되면_재고가_줄어든다() throws Exception {
		String token = loginAs("order-stock-down@example.com");
		int before = stockOf(13);

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":13,\"quantity\":3}"));
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isCreated());

		org.assertj.core.api.Assertions.assertThat(stockOf(13)).isEqualTo(before - 3);
	}

	@Test
	void 결제가_실패하면_402이고_재고가_되돌아온다() throws Exception {
		String token = loginAs("order-payment-fail@example.com");
		int before = stockOf(14);

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":14,\"quantity\":2}"));

		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.header("X-Force-Payment-Failure", "true")
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isPaymentRequired());

		// 실패한 주문은 남되 재고는 원래대로, 장바구니도 그대로여야 다시 시도할 수 있다.
		org.assertj.core.api.Assertions.assertThat(stockOf(14)).isEqualTo(before);
		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.content[0].status").value("FAILED"));
		mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void 같은_Idempotency_Key로_다시_요청하면_주문이_추가되지_않는다() throws Exception {
		String token = loginAs("order-idempotent@example.com");
		int before = stockOf(15);
		String key = "test-key-" + java.util.UUID.randomUUID();

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":15,\"quantity\":1}"));

		String first = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String orderNumber = objectMapper.readTree(first).get("orderNumber").asText();

		// 같은 키의 두 번째 요청은 새 주문이 아니라 첫 주문을 그대로 돌려준다.
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderNumber").value(orderNumber));

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.totalElements").value(1));
		org.assertj.core.api.Assertions.assertThat(stockOf(15)).isEqualTo(before - 1);
	}

	@Test
	void 결제_직전에_재고가_빠지면_409() throws Exception {
		String hoarder = loginAs("order-hoarder@example.com");
		int available = stockOf(16);

		// 남은 재고 전부를 장바구니에 담아 둔다. 담는 시점에는 통과한다.
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + hoarder)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":16,\"quantity\":" + available + "}"))
				.andExpect(status().isOk());

		// 그 사이 다른 사람이 하나를 사 간다.
		String buyer = loginAs("order-buyer@example.com");
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + buyer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":16,\"quantity\":1}"));
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + buyer)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + hoarder)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("재고가 부족합니다")));
	}

	@Test
	void 주문을_취소하면_재고가_돌아오고_상태가_CANCELLED가_된다() throws Exception {
		String token = loginAs("order-cancel@example.com");
		int before = stockOf(12);

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":12,\"quantity\":2}"));
		String createBody = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_ORDER_BODY))
				.andReturn().getResponse().getContentAsString();
		String orderNumber = objectMapper.readTree(createBody).get("orderNumber").asText();

		mockMvc.perform(post("/api/orders/" + orderNumber + "/cancel")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		org.assertj.core.api.Assertions.assertThat(stockOf(12)).isEqualTo(before);
		mockMvc.perform(get("/api/orders/" + orderNumber).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		// 이미 취소된 주문은 다시 취소할 수 없다 -- 재고가 두 번 복구되면 없는 재고가 생긴다.
		mockMvc.perform(post("/api/orders/" + orderNumber + "/cancel")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict());
	}

}
