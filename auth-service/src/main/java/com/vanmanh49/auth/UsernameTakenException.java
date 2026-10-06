package com.vanmanh49.auth;

public class UsernameTakenException extends RuntimeException {

	public UsernameTakenException(String username) {
		super("Username '" + username + "' is already taken");
	}

}
