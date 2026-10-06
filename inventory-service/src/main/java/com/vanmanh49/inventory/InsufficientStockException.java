package com.vanmanh49.inventory;

public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(Long productId) {
		super("Insufficient stock for product " + productId);
	}

}
