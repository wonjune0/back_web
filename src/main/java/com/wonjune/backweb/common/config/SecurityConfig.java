package com.wonjune.backweb.common.config;

import com.wonjune.backweb.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless chain, CSRF off (no cookie auth). Auth endpoints, health check and
 * actuator are open; everything else under /api/** requires a valid JWT, checked
 * by JwtAuthenticationFilter ahead of the standard username/password filter.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			// Delegates to Spring MVC's CORS registry, which only has mappings under the
			// local profile (LocalCorsConfig). In prod that registry is empty, so this
			// adds no headers and lets no preflight through -- the filter chain still
			// has to allow a request on its own merits.
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/error", "/actuator/**", "/api/auth/signup", "/api/auth/login",
						"/api/products", "/api/products/**", "/api/categories").permitAll()
				.anyRequest().authenticated()
			)
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, authException) -> {
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.getWriter().write("{\"message\":\"인증이 필요합니다\"}");
				})
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
