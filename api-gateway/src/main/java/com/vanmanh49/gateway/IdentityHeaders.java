package com.vanmanh49.gateway;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Tells downstream services who is calling. Services trust these headers, so whatever the
 * client sent under the same names is always removed first; the values come only from a
 * token this gateway has validated.
 */
public final class IdentityHeaders {

	public static final String USER_ID = "X-User-Id";

	public static final String USER_ROLES = "X-User-Roles";

	private IdentityHeaders() {
	}

	public static ServerRequest apply(ServerRequest request) {
		Jwt jwt = request.principal()
			.filter(JwtAuthenticationToken.class::isInstance)
			.map((principal) -> ((JwtAuthenticationToken) principal).getToken())
			.orElse(null);
		return ServerRequest.from(request).headers((headers) -> {
			headers.remove(USER_ID);
			headers.remove(USER_ROLES);
			if (jwt != null) {
				headers.set(USER_ID, jwt.getSubject());
				List<String> roles = jwt.getClaimAsStringList("roles");
				headers.set(USER_ROLES, (roles != null) ? String.join(",", roles) : "");
			}
		}).build();
	}

}
