package com.vanmanh49.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Answers for a service that is down or whose circuit is open. */
@RestController
public class FallbackController {

	// No method restriction: the forwarded request keeps its original HTTP method.
	@RequestMapping("/fallback")
	ResponseEntity<ProblemDetail> fallback() {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable. Please try again shortly.");
		body.setTitle("Service unavailable");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

}
