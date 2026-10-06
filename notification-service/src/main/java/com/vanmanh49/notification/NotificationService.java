package com.vanmanh49.notification;

import java.util.List;

import com.vanmanh49.notification.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	/**
	 * Records a notification for the event. Kafka can deliver a message more than once, so
	 * an event that was already handled is ignored; the unique constraint on the event id
	 * backs this check up if two deliveries race.
	 */
	@Transactional
	public void handle(OrderEvent event) {
		if (this.notifications.existsByEventId(event.eventId())) {
			log.info("Ignoring duplicate event {}", event.eventId());
			return;
		}
		Notification notification = this.notifications
			.save(new Notification(event.eventId(), event.orderId(), event.userId(), event.type(), message(event)));
		// A real system would send an email or push message here.
		log.info("Notifying user {}: {}", notification.getUserId(), notification.getMessage());
	}

	@Transactional(readOnly = true)
	public List<Notification> listFor(String userId) {
		return this.notifications.findByUserIdOrderByIdDesc(userId);
	}

	private static String message(OrderEvent event) {
		return switch (event.type()) {
			case "ORDER_PLACED" -> "Order #%d is confirmed. Total: %s".formatted(event.orderId(), event.total());
			case "ORDER_CANCELLED" -> "Order #%d was cancelled.".formatted(event.orderId());
			default -> "Order #%d was updated (%s).".formatted(event.orderId(), event.type());
		};
	}

}
