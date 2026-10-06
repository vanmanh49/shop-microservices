package com.vanmanh49.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

	private final KafkaTemplate<String, OrderEvent> kafka;

	private final String topic;

	public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafka,
			@Value("${shop.kafka.order-events-topic}") String topic) {
		this.kafka = kafka;
		this.topic = topic;
	}

	/**
	 * Sends the event keyed by order id, which keeps one order's events in order. The
	 * order is already committed, so a failed send is logged and not rethrown.
	 */
	public void publish(OrderEvent event) {
		try {
			this.kafka.send(this.topic, String.valueOf(event.orderId()), event).whenComplete((result, failure) -> {
				if (failure != null) {
					log.error("Could not publish {} for order {}", event.type(), event.orderId(), failure);
				}
			});
		}
		catch (RuntimeException ex) {
			log.error("Could not publish {} for order {}", event.type(), event.orderId(), ex);
		}
	}

}
