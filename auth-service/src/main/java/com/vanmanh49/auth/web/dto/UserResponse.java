package com.vanmanh49.auth.web.dto;

import com.vanmanh49.auth.user.User;

public record UserResponse(Long id, String username, String email, String role) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
	}

}
