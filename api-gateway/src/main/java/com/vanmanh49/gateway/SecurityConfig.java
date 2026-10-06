package com.vanmanh49.gateway;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The only place where tokens are checked: services behind the gateway are not reachable
 * from outside and rely on the identity headers it sets.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// Rules are evaluated top to bottom and the first match wins.
		http.authorizeHttpRequests((requests) -> requests
			// The circuit-breaker fallback is reached by an internal forward.
			.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
			.requestMatchers("/fallback").permitAll()
			.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
			// Reservations are made by order-service directly, never through the gateway.
			.requestMatchers("/api/inventory/reservations/**").denyAll()
			.requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
			.requestMatchers("/api/products/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("ADMIN")
			.requestMatchers("/api/**").authenticated()
			.anyRequest().denyAll());
		http.oauth2ResourceServer((resourceServer) -> resourceServer
			.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		// Every request carries its own token, so there is no session and no CSRF risk.
		http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.csrf((csrf) -> csrf.disable());
		return http.build();
	}

	// Maps the token's "roles" claim (["ADMIN"]) to authorities (ROLE_ADMIN).
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
