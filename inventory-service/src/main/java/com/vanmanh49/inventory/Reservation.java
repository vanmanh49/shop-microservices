package com.vanmanh49.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations")
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_ref", nullable = false, length = 64)
	private String orderRef;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private int quantity;

	protected Reservation() {
	}

	public Reservation(String orderRef, Long productId, int quantity) {
		this.orderRef = orderRef;
		this.productId = productId;
		this.quantity = quantity;
	}

	public Long getProductId() {
		return this.productId;
	}

	public int getQuantity() {
		return this.quantity;
	}

}
