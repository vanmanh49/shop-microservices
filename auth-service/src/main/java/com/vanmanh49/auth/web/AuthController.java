package com.vanmanh49.auth.web;

import com.vanmanh49.auth.AuthService;
import com.vanmanh49.auth.web.dto.LoginRequest;
import com.vanmanh49.auth.web.dto.RegisterRequest;
import com.vanmanh49.auth.web.dto.TokenResponse;
import com.vanmanh49.auth.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return this.authService.register(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return this.authService.login(request);
	}

	// The gateway sets X-User-Id from the validated token.
	@GetMapping("/me")
	public UserResponse me(@RequestHeader(name = "X-User-Id", required = false) String userId) {
		return this.authService.currentUser(userId);
	}

}
