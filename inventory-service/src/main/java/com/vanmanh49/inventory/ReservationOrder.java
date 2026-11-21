package com.vanmanh49.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Whether an order's stock is held or was released. A released order cannot reserve again. */
@Entity
@Table(name = "reservation_orders")
public class ReservationOrder {

	@Id
	@Column(name = "order_ref", length = 64)
	private String orderRef;

	@Column(nullable = false)
	private boolean released;

	protected ReservationOrder() {
	}

	public ReservationOrder(String orderRef) {
		this.orderRef = orderRef;
	}

	public boolean isReleased() {
		return this.released;
	}

	public void markReleased() {
		this.released = true;
	}

}
