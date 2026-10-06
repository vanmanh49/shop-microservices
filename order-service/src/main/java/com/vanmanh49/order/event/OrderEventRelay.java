package com.vanmanh49.order.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands order events to Kafka once the order's transaction has committed, so consumers
 * never hear about an order that was rolled back. If the service stops between the commit
 * and the send, the event is lost; a transactional outbox would close that gap.
 */
@Component
public class OrderEventRelay {

	private final OrderEventPublisher publisher;

	public OrderEventRelay(OrderEventPublisher publisher) {
		this.publisher = publisher;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCommitted(OrderEvent event) {
		this.publisher.publish(event);
	}

}
