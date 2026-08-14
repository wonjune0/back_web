package com.wonjune.backweb.category;

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
 * The storefront sidebar sends these exact names back as the parentCategory/category
 * query parameters, so the assertions pin the strings, not just the shape.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIT {

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
	void 인증없이_시드순서대로_트리를_반환한다() throws Exception {
		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].name").value("뷰티"))
				.andExpect(jsonPath("$[0].subcategories.length()").value(4))
				.andExpect(jsonPath("$[0].subcategories[0]").value("스킨케어"))
				.andExpect(jsonPath("$[1].name").value("식품"))
				.andExpect(jsonPath("$[1].subcategories.length()").value(3))
				.andExpect(jsonPath("$[4].name").value("스포츠/레저"))
				.andExpect(jsonPath("$[4].subcategories.length()").value(2));
	}

}
