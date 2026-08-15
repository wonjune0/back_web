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
				.andExpect(jsonPath("$.status").value("PLACED"))
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

}
