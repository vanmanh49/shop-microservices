package com.vanmanh49.order.client;

public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(Long productId) {
		super("Product " + productId + " does not exist");
	}

}
