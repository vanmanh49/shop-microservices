package com.vanmanh49.inventory;

public class StockNotFoundException extends RuntimeException {

	public StockNotFoundException(Long productId) {
		super("No stock record for product " + productId);
	}

}
