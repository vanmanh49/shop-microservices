package com.vanmanh49.order;

public class OrderAlreadyCancelledException extends RuntimeException {

	public OrderAlreadyCancelledException(Long id) {
		super("Order " + id + " is already cancelled");
	}

}
