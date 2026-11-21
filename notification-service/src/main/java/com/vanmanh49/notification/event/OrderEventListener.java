package com.vanmanh49.notification.event;

import java.time.Duration;

import com.vanmanh49.notification.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

@Component
public class OrderEventListener {

	private final NotificationService notifications;

	public OrderEventListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@KafkaListener(topics = "${shop.kafka.order-events-topic}")
	public void onOrderEvent(OrderEvent event) {
		this.notifications.handle(event);
	}

	/**
	 * Retries a failing record with growing pauses (1s, 2s, 4s, ... up to 30s apart), so a
	 * database restart does not lose events. A record whose data breaks a database rule, or
	 * that cannot be deserialized, is skipped at once. Anything else is given up after 15
	 * minutes, so one bad record cannot block the partition forever.
	 */
	@Bean
	static DefaultErrorHandler kafkaErrorHandler() {
		ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
		backOff.setMaxInterval(Duration.ofSeconds(30).toMillis());
		backOff.setMaxElapsedTime(Duration.ofMinutes(15).toMillis());
		DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
		handler.addNotRetryableExceptions(DataIntegrityViolationException.class);
		return handler;
	}

}
