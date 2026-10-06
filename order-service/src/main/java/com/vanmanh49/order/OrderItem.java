package com.vanmanh49.order;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One order line. Name and price are snapshots taken when the order was placed. */
@Entity
@Table(name = "order_items")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_name", nullable = false)
	private String productName;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false)
	private int quantity;

	protected OrderItem() {
	}

	public OrderItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
		this.productId = productId;
		this.productName = productName;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
	}

	void attachTo(Order order) {
		this.order = order;
	}

	public BigDecimal lineTotal() {
		return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
	}

	public Long getProductId() {
		return this.productId;
	}

	public String getProductName() {
		return this.productName;
	}

	public BigDecimal getUnitPrice() {
		return this.unitPrice;
	}

	public int getQuantity() {
		return this.quantity;
	}

}
