package com.vanmanh49.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true)
	private UUID eventId;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(nullable = false, length = 40)
	private String type;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(UUID eventId, Long orderId, String userId, String type, String message) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.userId = userId;
		this.type = type;
		this.message = message;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public Long getOrderId() {
		return this.orderId;
	}

	public String getUserId() {
		return this.userId;
	}

	public String getType() {
		return this.type;
	}

	public String getMessage() {
		return this.message;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
