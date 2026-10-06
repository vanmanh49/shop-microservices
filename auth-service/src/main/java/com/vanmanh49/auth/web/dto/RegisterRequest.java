package com.vanmanh49.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// BCrypt only reads the first 72 bytes of a password, hence the upper bound.
public record RegisterRequest(@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Email String email) {
}
