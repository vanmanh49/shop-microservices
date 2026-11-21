package com.vanmanh49.inventory;

public class OrderReleasedException extends RuntimeException {

	public OrderReleasedException(String orderRef) {
		super("The reservation for order " + orderRef + " was already released");
	}

}
