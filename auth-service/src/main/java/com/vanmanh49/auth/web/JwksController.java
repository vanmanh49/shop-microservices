package com.vanmanh49.auth.web;

import java.util.Map;

import com.vanmanh49.auth.token.JwtKeys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

	private final JwtKeys keys;

	public JwksController(JwtKeys keys) {
		this.keys = keys;
	}

	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return this.keys.publicKeys().toJSONObject();
	}

}
