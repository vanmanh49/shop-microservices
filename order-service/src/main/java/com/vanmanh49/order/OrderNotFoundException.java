package com.vanmanh49.order;

public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException(Long id) {
		super("Order " + id + " was not found");
	}

}
