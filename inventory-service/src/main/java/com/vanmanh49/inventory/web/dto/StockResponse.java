package com.vanmanh49.inventory.web.dto;

import com.vanmanh49.inventory.InventoryItem;

public record StockResponse(Long productId, int available) {

	public static StockResponse from(InventoryItem item) {
		return new StockResponse(item.getProductId(), item.getAvailable());
	}

}
