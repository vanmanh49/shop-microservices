package com.vanmanh49.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.vanmanh49.order.Order;

/**
 * The message published to the order events topic. This record is the contract with
 * consumers, which keep their own copy of it: add fields freely, never rename or remove.
 */
public record OrderEvent(UUID eventId, String type, Long orderId, String userId, BigDecimal total,
		Instant occurredAt) {

	public static final String ORDER_PLACED = "ORDER_PLACED";

	public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

	public static OrderEvent of(String type, Order order) {
		return new OrderEvent(UUID.randomUUID(), type, order.getId(), order.getUserId(), order.getTotal(),
				Instant.now());
	}

}
