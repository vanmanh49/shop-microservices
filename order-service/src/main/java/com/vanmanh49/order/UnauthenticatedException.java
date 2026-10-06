package com.vanmanh49.order;

public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Authentication is required");
	}

}
