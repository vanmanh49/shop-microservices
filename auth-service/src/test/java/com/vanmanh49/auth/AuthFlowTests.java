package com.vanmanh49.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void registerReturns201AndHidesPassword() throws Exception {
		register("alice", "password1").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerDuplicateUsernameReturns409() throws Exception {
		register("alice", "password1").andExpect(status().isCreated());

		register("alice", "password2").andExpect(status().isConflict());
	}

	@Test
	void registerShortPasswordReturns400() throws Exception {
		register("alice", "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void loginReturnsTokenThatVerifiesAgainstJwks() throws Exception {
		String registered = register("alice", "password1").andReturn().getResponse().getContentAsString();
		Number userId = JsonPath.read(registered, "$.id");

		String body = login("alice", "password1").andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(3600))
			.andReturn()
			.getResponse()
			.getContentAsString();

		Jwt jwt = decodeWithPublishedKey(JsonPath.read(body, "$.accessToken"));
		assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
		assertThat(jwt.getClaimAsString("username")).isEqualTo("alice");
		assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("USER"));
	}

	@Test
	void loginWrongPasswordReturns401() throws Exception {
		register("alice", "password1");

		login("alice", "wrong-password").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
	}

	@Test
	void loginUnknownUserReturns401() throws Exception {
		login("nobody", "password1").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
	}

	@Test
	void adminIsSeeded() throws Exception {
		String body = login("admin", "admin12345").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		Jwt jwt = decodeWithPublishedKey(JsonPath.read(body, "$.accessToken"));
		assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN"));
	}

	@Test
	void meReturnsCurrentUser() throws Exception {
		String registered = register("alice", "password1").andReturn().getResponse().getContentAsString();
		Number userId = JsonPath.read(registered, "$.id");

		this.mvc.perform(get("/api/auth/me").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("alice"));
	}

	@Test
	void meWithoutIdentityHeaderReturns401() throws Exception {
		this.mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	private ResultActions register(String username, String password) throws Exception {
		String json = """
				{"username":"%s","password":"%s","email":"%s@example.com"}""".formatted(username, password, username);
		return this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions login(String username, String password) throws Exception {
		String json = """
				{"username":"%s","password":"%s"}""".formatted(username, password);
		return this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Jwt decodeWithPublishedKey(String token) throws Exception {
		String jwks = this.mvc.perform(get("/.well-known/jwks.json"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		RSAPublicKey key = JWKSet.parse(jwks).getKeys().get(0).toRSAKey().toRSAPublicKey();
		return NimbusJwtDecoder.withPublicKey(key).build().decode(token);
	}

}
