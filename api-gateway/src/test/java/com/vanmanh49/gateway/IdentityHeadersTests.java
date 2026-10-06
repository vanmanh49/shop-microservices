package com.vanmanh49.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

class IdentityHeadersTests {

	@Test
	void replacesClientSuppliedHeaders() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/orders");
		servletRequest.addHeader("X-User-Id", "999");
		servletRequest.addHeader("x-user-roles", "ADMIN");
		servletRequest.addHeader("Accept", "application/json");
		servletRequest.setUserPrincipal(new JwtAuthenticationToken(jwt("42", List.of("USER"))));

		ServerRequest result = IdentityHeaders.apply(ServerRequest.create(servletRequest, List.of()));

		assertThat(result.headers().header("X-User-Id")).containsExactly("42");
		assertThat(result.headers().header("X-User-Roles")).containsExactly("USER");
		assertThat(result.headers().header("Accept")).containsExactly("application/json");
	}

	@Test
	void joinsSeveralRoles() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/orders");
		servletRequest.setUserPrincipal(new JwtAuthenticationToken(jwt("1", List.of("USER", "ADMIN"))));

		ServerRequest result = IdentityHeaders.apply(ServerRequest.create(servletRequest, List.of()));

		assertThat(result.headers().header("X-User-Roles")).containsExactly("USER,ADMIN");
	}

	@Test
	void stripsHeadersWhenAnonymous() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/products");
		servletRequest.addHeader("X-User-Id", "999");
		servletRequest.addHeader("X-User-Roles", "ADMIN");

		ServerRequest result = IdentityHeaders.apply(ServerRequest.create(servletRequest, List.of()));

		assertThat(result.headers().header("X-User-Id")).isEmpty();
		assertThat(result.headers().header("X-User-Roles")).isEmpty();
	}

	private static Jwt jwt(String subject, List<String> roles) {
		return Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.subject(subject)
			.claim("roles", roles)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
	}

}
