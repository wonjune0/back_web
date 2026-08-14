package com.wonjune.backweb.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Relies on the Flyway-seeded catalog (V5__seed_products.sql, 16 products
 * transcribed from front_web's mock data) rather than hand-built fixtures,
 * so this doubles as a check that the seed migration matches the frontend contract.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIT {

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

	@Test
	void 목록조회_기본값은_16개상품을_id순으로_반환() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(16))
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].name").value("수분 진정 크림 100ml, 1개"))
				.andExpect(jsonPath("$.content[0].parentCategory").value("뷰티"))
				.andExpect(jsonPath("$.content[0].category").value("스킨케어"));
	}

	@Test
	void search는_카테고리필터보다_우선한다() throws Exception {
		// "크림" matches both 수분 진정 크림(1) and 수딩 선크림(5); had the 간식/과자
		// filter been applied instead of ignored, neither would come back.
		mockMvc.perform(get("/api/products").param("search", "크림").param("category", "간식/과자"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[1].id").value(5));
	}

	@Test
	void category_필터는_leaf정확히일치() throws Exception {
		mockMvc.perform(get("/api/products").param("category", "스킨케어"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void parentCategory_필터() throws Exception {
		mockMvc.perform(get("/api/products").param("parentCategory", "뷰티"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(6));
	}

	@Test
	void sort_price_asc() throws Exception {
		mockMvc.perform(get("/api/products").param("sort", "price-asc").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("순면 화장솜, 300매, 1개"));
	}

	@Test
	void sort_reviews_desc() throws Exception {
		mockMvc.perform(get("/api/products").param("sort", "reviews").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].reviewCount").value(15234));
	}

	@Test
	void 상세조회_성공() throws Exception {
		mockMvc.perform(get("/api/products/16"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("원터치 텐트, 2~3인용, 1개"))
				.andExpect(jsonPath("$.detailDescription").isNotEmpty());
	}

	@Test
	void 존재하지않는상품은_404() throws Exception {
		mockMvc.perform(get("/api/products/9999"))
				.andExpect(status().isNotFound());
	}

}
