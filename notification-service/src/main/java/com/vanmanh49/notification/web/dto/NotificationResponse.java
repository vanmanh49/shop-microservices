package com.vanmanh49.notification.web.dto;

import java.time.Instant;

import com.vanmanh49.notification.Notification;

public record NotificationResponse(Long id, Long orderId, String type, String message, Instant createdAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getOrderId(), notification.getType(),
				notification.getMessage(), notification.getCreatedAt());
	}

}
