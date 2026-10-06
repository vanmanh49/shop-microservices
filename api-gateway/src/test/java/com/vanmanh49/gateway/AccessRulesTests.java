package com.vanmanh49.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks who may call what. No backend is running, so a request that passes the access
 * rules ends in the circuit-breaker fallback; "allowed" therefore means "not 401 or 403".
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRulesTests {

	private static final String USER = "user-token";

	private static final String ADMIN = "admin-token";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void knownTokens() {
		given(this.jwtDecoder.decode(USER)).willReturn(jwt(USER, "42", "USER"));
		given(this.jwtDecoder.decode(ADMIN)).willReturn(jwt(ADMIN, "1", "ADMIN"));
		given(this.jwtDecoder.decode("garbage")).willThrow(new BadJwtException("Malformed token"));
		given(this.jwtDecoder.decode("expired")).willThrow(new BadJwtException("Jwt expired"));
	}

	@Test
	void loginAndRegisterArePublic() throws Exception {
		assertAllowed(post("/api/auth/login"));
		assertAllowed(post("/api/auth/register"));
	}

	@Test
	void readingProductsIsPublic() throws Exception {
		assertAllowed(get("/api/products"));
		assertAllowed(get("/api/products/1"));
	}

	@Test
	void healthIsPublic() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void ordersRequireAuthentication() throws Exception {
		this.mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
		this.mvc.perform(post("/api/orders")).andExpect(status().isUnauthorized());
		assertAllowed(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER));
	}

	@Test
	void notificationsAndProfileRequireAuthentication() throws Exception {
		this.mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		assertAllowed(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER));
		assertAllowed(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER));
	}

	@Test
	void writingProductsRequiresAdmin() throws Exception {
		this.mvc.perform(post("/api/products")).andExpect(status().isUnauthorized());
		this.mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER))
			.andExpect(status().isForbidden());
		this.mvc.perform(put("/api/products/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER))
			.andExpect(status().isForbidden());
		this.mvc.perform(delete("/api/products/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER))
			.andExpect(status().isForbidden());
		assertAllowed(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN));
		assertAllowed(delete("/api/products/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN));
	}

	@Test
	void settingStockRequiresAdmin() throws Exception {
		this.mvc.perform(put("/api/inventory/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER))
			.andExpect(status().isForbidden());
		assertAllowed(put("/api/inventory/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN));
	}

	@Test
	void readingStockRequiresAuthentication() throws Exception {
		this.mvc.perform(get("/api/inventory/1")).andExpect(status().isUnauthorized());
		assertAllowed(get("/api/inventory/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER));
	}

	@Test
	void reservationEndpointsAreNeverExposed() throws Exception {
		this.mvc.perform(post("/api/inventory/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
			.andExpect(status().isForbidden());
		this.mvc
			.perform(delete("/api/inventory/reservations/some-ref").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + ADMIN))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/api/inventory/reservations")).andExpect(status().isUnauthorized());
	}

	@Test
	void malformedOrExpiredTokenReturns401() throws Exception {
		this.mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
			.andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void badTokenIsRejectedEvenOnPublicPaths() throws Exception {
		this.mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void pathsOutsideTheApiAreNotServed() throws Exception {
		this.mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/internal").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
			.andExpect(status().isForbidden());
	}

	@Test
	void fallbackReturns503ProblemDetail() throws Exception {
		this.mvc.perform(get("/fallback"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.title").value("Service unavailable"))
			.andExpect(jsonPath("$.status").value(503));
	}

	private void assertAllowed(MockHttpServletRequestBuilder request) throws Exception {
		int status = this.mvc.perform(request).andReturn().getResponse().getStatus();
		assertThat(status).isNotIn(401, 403);
	}

	private static Jwt jwt(String token, String subject, String role) {
		return Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(subject)
			.claim("roles", List.of(role))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
	}

}
