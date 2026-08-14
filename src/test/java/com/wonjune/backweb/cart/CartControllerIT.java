package com.wonjune.backweb.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class CartControllerIT {

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

	/**
	 * A cart lives as long as its owner, and nothing truncates tables between tests, so
	 * every test signs up its own user -- same approach as OrderControllerIT. Sharing one
	 * account here made the assertions depend on method execution order.
	 */
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

	@Test
	void 빈장바구니_조회시_빈응답() throws Exception {
		String accessToken = loginAs("cart-empty@example.com");

		mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalQuantity").value(0))
				.andExpect(jsonPath("$.totalPrice").value(0));
	}

	@Test
	void 담기는_동일상품_추가시_수량이_누적된다() throws Exception {
		String accessToken = loginAs("cart-add@example.com");

		String addBody = "{\"productId\":1,\"quantity\":2}";
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content(addBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].quantity").value(2));

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content(addBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].quantity").value(4))
				.andExpect(jsonPath("$.totalQuantity").value(4));
	}

	@Test
	void 수량변경은_절대값으로_설정된다() throws Exception {
		String accessToken = loginAs("cart-update@example.com");

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":2,\"quantity\":3}"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/cart/items/2").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].quantity").value(10));
	}

	@Test
	void 삭제와_전체비우기() throws Exception {
		String accessToken = loginAs("cart-delete@example.com");

		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":3,\"quantity\":1}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":4,\"quantity\":1}"))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/cart/items/3").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1));

		mockMvc.perform(delete("/api/cart").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void 토큰없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
	}

}
