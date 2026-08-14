package com.wonjune.backweb.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Local development only. In production CloudFront serves the static site and proxies
 * /api/* to the ALB under the same domain, so browser requests are same-origin and no
 * CORS headers are involved. Running the frontend from a separate static server locally
 * is the only case that needs them, hence the profile guard -- the deployed prod profile
 * never registers this mapping.
 */
@Configuration
@Profile("local")
public class LocalCorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
				.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}

}
