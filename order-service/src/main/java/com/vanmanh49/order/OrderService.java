package com.vanmanh49.order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vanmanh49.order.client.DependencyUnavailableException;
import com.vanmanh49.order.client.InventoryClient;
import com.vanmanh49.order.client.ProductClient;
import com.vanmanh49.order.client.dto.ProductDto;
import com.vanmanh49.order.client.dto.ReservationItem;
import com.vanmanh49.order.web.dto.OrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the order flow across product-service, inventory-service and the local
 * database. There is no distributed transaction: a step that fails after stock was
 * reserved is compensated by releasing the reservation.
 */
@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final ProductClient productClient;

	private final InventoryClient inventoryClient;

	private final OrderStore store;

	public OrderService(ProductClient productClient, InventoryClient inventoryClient, OrderStore store) {
		this.productClient = productClient;
		this.inventoryClient = inventoryClient;
		this.store = store;
	}

	public Order place(String userId, List<OrderLine> lines) {
		Map<Long, Integer> quantities = new LinkedHashMap<>();
		for (OrderLine line : lines) {
			quantities.merge(line.productId(), line.quantity(), Integer::sum);
		}
		List<OrderItem> items = new ArrayList<>();
		List<ReservationItem> reservation = new ArrayList<>();
		quantities.forEach((productId, quantity) -> {
			ProductDto product = this.productClient.get(productId);
			items.add(new OrderItem(product.id(), product.name(), product.price(), quantity));
			reservation.add(new ReservationItem(productId, quantity));
		});

		UUID orderRef = UUID.randomUUID();
		try {
			this.inventoryClient.reserve(orderRef.toString(), reservation);
		}
		catch (DependencyUnavailableException ex) {
			// A timed-out call may still have reserved the stock on the other side.
			releaseQuietly(orderRef);
			throw ex;
		}
		try {
			return this.store.saveConfirmed(orderRef, userId, items);
		}
		catch (RuntimeException ex) {
			releaseQuietly(orderRef);
			throw ex;
		}
	}

	public Order cancel(Long id, String userId) {
		Order order = this.store.getOwned(id, userId);
		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new OrderAlreadyCancelledException(id);
		}
		this.inventoryClient.release(order.getOrderRef().toString());
		return this.store.markCancelled(id, userId);
	}

	public Order get(Long id, String userId) {
		return this.store.getOwned(id, userId);
	}

	public List<Order> list(String userId) {
		return this.store.listOwned(userId);
	}

	private void releaseQuietly(UUID orderRef) {
		try {
			this.inventoryClient.release(orderRef.toString());
		}
		catch (RuntimeException ex) {
			log.error("Could not release the reservation for order {}; stock stays reserved until it is released"
					+ " manually", orderRef, ex);
		}
	}

}
