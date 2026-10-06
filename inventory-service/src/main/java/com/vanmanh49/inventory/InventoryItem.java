package com.vanmanh49.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

	@Id
	@Column(name = "product_id")
	private Long productId;

	@Column(nullable = false)
	private int available;

	protected InventoryItem() {
	}

	public InventoryItem(Long productId, int available) {
		this.productId = productId;
		this.available = available;
	}

	public Long getProductId() {
		return this.productId;
	}

	public int getAvailable() {
		return this.available;
	}

	public void setAvailable(int available) {
		this.available = available;
	}

}
