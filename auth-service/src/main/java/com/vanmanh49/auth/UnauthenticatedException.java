package com.vanmanh49.auth;

public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Authentication is required");
	}

}
