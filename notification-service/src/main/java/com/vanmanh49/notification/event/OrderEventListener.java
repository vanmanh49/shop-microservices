package com.vanmanh49.notification.event;

import com.vanmanh49.notification.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

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
	 * Retries a failing record twice, one second apart, then logs and skips it so that one
	 * bad record cannot block the partition. Records that cannot be deserialized are
	 * skipped without retries.
	 */
	@Bean
	static DefaultErrorHandler kafkaErrorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
	}

}
