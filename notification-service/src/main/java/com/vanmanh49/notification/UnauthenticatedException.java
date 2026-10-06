package com.vanmanh49.notification;

public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Authentication is required");
	}

}
