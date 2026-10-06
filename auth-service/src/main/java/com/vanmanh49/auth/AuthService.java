package com.vanmanh49.auth;

import java.util.Optional;

import com.vanmanh49.auth.token.TokenService;
import com.vanmanh49.auth.user.Role;
import com.vanmanh49.auth.user.User;
import com.vanmanh49.auth.user.UserRepository;
import com.vanmanh49.auth.web.dto.LoginRequest;
import com.vanmanh49.auth.web.dto.RegisterRequest;
import com.vanmanh49.auth.web.dto.TokenResponse;
import com.vanmanh49.auth.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (this.users.existsByUsername(request.username())) {
			throw new UsernameTakenException(request.username());
		}
		User user = new User(request.username(), request.email(), this.passwordEncoder.encode(request.password()),
				Role.USER);
		return UserResponse.from(this.users.save(user));
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		User user = this.users.findByUsername(request.username())
			.filter((candidate) -> this.passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
			.orElseThrow(BadCredentialsException::new);
		return this.tokens.issue(user);
	}

	@Transactional(readOnly = true)
	public UserResponse currentUser(String userId) {
		return parseId(userId).flatMap(this.users::findById)
			.map(UserResponse::from)
			.orElseThrow(UnauthenticatedException::new);
	}

	private static Optional<Long> parseId(String userId) {
		try {
			return Optional.of(Long.valueOf(userId));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

}
