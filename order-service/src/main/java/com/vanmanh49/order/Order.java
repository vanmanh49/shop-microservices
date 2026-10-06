package com.vanmanh49.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Incremented on every update. Two requests changing the same order at once (two
	// overlapping cancels) cannot both win: the slower one fails instead of overwriting.
	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "order_ref", nullable = false, unique = true)
	private UUID orderRef;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal total;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<OrderItem> items = new ArrayList<>();

	protected Order() {
	}

	public Order(UUID orderRef, String userId, List<OrderItem> items) {
		this.orderRef = orderRef;
		this.userId = userId;
		this.status = OrderStatus.CONFIRMED;
		this.createdAt = Instant.now();
		this.total = BigDecimal.ZERO;
		for (OrderItem item : items) {
			item.attachTo(this);
			this.items.add(item);
			this.total = this.total.add(item.lineTotal());
		}
	}

	public void cancel() {
		if (this.status == OrderStatus.CANCELLED) {
			throw new OrderAlreadyCancelledException(this.id);
		}
		this.status = OrderStatus.CANCELLED;
	}

	public Long getId() {
		return this.id;
	}

	public UUID getOrderRef() {
		return this.orderRef;
	}

	public String getUserId() {
		return this.userId;
	}

	public OrderStatus getStatus() {
		return this.status;
	}

	public BigDecimal getTotal() {
		return this.total;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public List<OrderItem> getItems() {
		return this.items;
	}

}
