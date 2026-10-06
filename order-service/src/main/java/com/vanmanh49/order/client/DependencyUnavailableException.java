package com.vanmanh49.order.client;

public class DependencyUnavailableException extends RuntimeException {

	public DependencyUnavailableException(String service, Throwable cause) {
		super(service + " is unavailable", cause);
	}

}
