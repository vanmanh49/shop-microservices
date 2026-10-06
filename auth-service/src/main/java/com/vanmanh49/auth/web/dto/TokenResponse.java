package com.vanmanh49.auth.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
