package com.vanmanh49.auth.token;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.vanmanh49.auth.SecurityProperties;
import com.vanmanh49.auth.user.User;
import com.vanmanh49.auth.web.dto.TokenResponse;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Duration ttl;

	public TokenService(JwtKeys keys, SecurityProperties properties) {
		this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(keys.signingKeys()));
		this.ttl = properties.jwt().ttl();
	}

	public TokenResponse issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("auth-service")
			.subject(String.valueOf(user.getId()))
			.issuedAt(now)
			.expiresAt(now.plus(this.ttl))
			.claim("username", user.getUsername())
			.claim("roles", List.of(user.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		String token = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", this.ttl.toSeconds());
	}

}
