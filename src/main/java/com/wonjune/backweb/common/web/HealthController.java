package com.wonjune.backweb.common.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ALB target group health check calls GET / over HTTP and expects 200.
 * See modules/alb_module/main.tf in the terraform repo (matcher = "200").
 */
@RestController
public class HealthController {

	@GetMapping("/")
	public ResponseEntity<Void> health() {
		return ResponseEntity.ok().build();
	}

}
