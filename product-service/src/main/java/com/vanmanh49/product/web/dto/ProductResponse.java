package com.vanmanh49.product.web.dto;

import java.math.BigDecimal;

import com.vanmanh49.product.Product;

public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
				product.getPrice());
	}

}
