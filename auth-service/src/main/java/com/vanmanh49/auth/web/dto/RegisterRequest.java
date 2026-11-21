package com.vanmanh49.auth.web.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Email String email) {

	// BCrypt reads at most 72 bytes, and a letter such as "ệ" takes 3 bytes in UTF-8.
	@AssertTrue(message = "password must be at most 72 bytes")
	public boolean isPasswordAtMost72Bytes() {
		return this.password == null || this.password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

}
