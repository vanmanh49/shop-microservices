package com.vanmanh49.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This service's view of the messages order-service publishes. It is a separate copy on
 * purpose: the JSON on the topic is the contract, not a shared class, and fields this
 * service does not know are ignored.
 */
public record OrderEvent(UUID eventId, String type, Long orderId, String userId, BigDecimal total,
		Instant occurredAt) {
}
