package com.vanmanh49.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vanmanh49.inventory.web.dto.ReservationItem;
import com.vanmanh49.inventory.web.dto.ReservationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

	private final InventoryItemRepository items;

	private final ReservationRepository reservations;

	private final ReservationOrderRepository orders;

	public InventoryService(InventoryItemRepository items, ReservationRepository reservations,
			ReservationOrderRepository orders) {
		this.items = items;
		this.reservations = reservations;
		this.orders = orders;
	}

	@Transactional(readOnly = true)
	public InventoryItem get(Long productId) {
		return this.items.findById(productId).orElseThrow(() -> new StockNotFoundException(productId));
	}

	public InventoryItem setStock(Long productId, int quantity) {
		InventoryItem item = this.items.findById(productId).orElseGet(() -> new InventoryItem(productId, 0));
		item.setAvailable(quantity);
		return this.items.save(item);
	}

	/**
	 * Reserves every item or none: a short item throws, which rolls back the decrements
	 * already made in this transaction. Reserving an order again does nothing, and an order
	 * that was released is refused.
	 */
	public void reserve(ReservationRequest request) {
		Optional<ReservationOrder> existing = this.orders.findById(request.orderRef());
		if (existing.isPresent()) {
			if (existing.get().isReleased()) {
				throw new OrderReleasedException(request.orderRef());
			}
			return;
		}
		// Taking the order's row first makes a concurrent release wait for this transaction.
		this.orders.insertReserved(request.orderRef());
		Map<Long, Integer> quantities = new LinkedHashMap<>();
		for (ReservationItem item : request.items()) {
			quantities.merge(item.productId(), item.quantity(), Integer::sum);
		}
		quantities.forEach((productId, quantity) -> {
			if (this.items.decrement(productId, quantity) == 0) {
				throw new InsufficientStockException(productId);
			}
			this.reservations.save(new Reservation(request.orderRef(), productId, quantity));
		});
	}

	/**
	 * Returns the order's stock once. The order is marked released even if it has no
	 * reservation yet, so a reservation that arrives late is refused.
	 */
	public void release(String orderRef) {
		this.orders.insertReleasedIfAbsent(orderRef);
		ReservationOrder order = this.orders.lock(orderRef);
		if (order.isReleased()) {
			return;
		}
		order.markReleased();
		List<Reservation> held = this.reservations.findByOrderRef(orderRef);
		held.forEach((reservation) -> this.items.increment(reservation.getProductId(), reservation.getQuantity()));
		this.reservations.deleteAllInBatch(held);
	}

}
