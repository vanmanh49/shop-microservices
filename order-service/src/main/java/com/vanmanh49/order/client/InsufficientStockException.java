package com.vanmanh49.order.client;

public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException() {
		super("Insufficient stock for one or more items");
	}

}
