package com.vanmanh49.auth.token;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

/**
 * Holds the RSA key pair that signs access tokens. The pair is generated at startup, so
 * tokens stop verifying when the service restarts and every instance has its own key. A
 * real deployment would load a shared key from a secret store instead.
 */
@Component
public class JwtKeys {

	private final RSAKey rsaKey;

	public JwtKeys() {
		KeyPair keyPair = generateKeyPair();
		this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
			.privateKey((RSAPrivateKey) keyPair.getPrivate())
			.keyID(UUID.randomUUID().toString())
			.build();
	}

	/** Key set including the private key, for signing. */
	public JWKSet signingKeys() {
		return new JWKSet(this.rsaKey);
	}

	/** Key set with public keys only, safe to publish. */
	public JWKSet publicKeys() {
		return signingKeys().toPublicJWKSet();
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
