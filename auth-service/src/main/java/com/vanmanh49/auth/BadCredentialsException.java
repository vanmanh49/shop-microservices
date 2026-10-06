package com.vanmanh49.auth;

public class BadCredentialsException extends RuntimeException {

	public BadCredentialsException() {
		super("Invalid username or password");
	}

}
