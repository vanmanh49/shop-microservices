package com.vanmanh49.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.security")
public record SecurityProperties(Jwt jwt, Admin admin) {

	public record Jwt(Duration ttl) {
	}

	public record Admin(String username, String password) {
	}

}
