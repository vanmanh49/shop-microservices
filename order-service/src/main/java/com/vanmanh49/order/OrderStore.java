package com.vanmanh49.order;

import java.util.List;
import java.util.UUID;

import com.vanmanh49.order.event.OrderEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of the order flow. It is a separate bean from {@link OrderService} so
 * that each method is one transaction and the service can compensate when one fails.
 * Events published here are delivered to Kafka only after the transaction commits.
 */
@Component
public class OrderStore {

	private final OrderRepository orders;

	private final ApplicationEventPublisher events;

	public OrderStore(OrderRepository orders, ApplicationEventPublisher events) {
		this.orders = orders;
		this.events = events;
	}

	@Transactional
	public Order saveConfirmed(UUID orderRef, String userId, List<OrderItem> items) {
		Order order = this.orders.save(new Order(orderRef, userId, items));
		this.events.publishEvent(OrderEvent.of(OrderEvent.ORDER_PLACED, order));
		return order;
	}

	@Transactional
	public Order markCancelled(Long id, String userId) {
		Order order = getOwned(id, userId);
		order.cancel();
		this.events.publishEvent(OrderEvent.of(OrderEvent.ORDER_CANCELLED, order));
		return order;
	}

	/** Another user's order is reported as missing, so ids cannot be probed. */
	@Transactional(readOnly = true)
	public Order getOwned(Long id, String userId) {
		return this.orders.findByIdAndUserId(id, userId).orElseThrow(() -> new OrderNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<Order> listOwned(String userId) {
		return this.orders.findByUserIdOrderByIdDesc(userId);
	}

}
