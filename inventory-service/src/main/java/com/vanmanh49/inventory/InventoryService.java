package com.vanmanh49.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vanmanh49.inventory.web.dto.ReservationItem;
import com.vanmanh49.inventory.web.dto.ReservationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

	private final InventoryItemRepository items;

	private final ReservationRepository reservations;

	public InventoryService(InventoryItemRepository items, ReservationRepository reservations) {
		this.items = items;
		this.reservations = reservations;
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
	 * already made in this transaction.
	 */
	public void reserve(ReservationRequest request) {
		if (this.reservations.existsByOrderRef(request.orderRef())) {
			return;
		}
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

	public void release(String orderRef) {
		List<Reservation> held = this.reservations.findByOrderRef(orderRef);
		held.forEach((reservation) -> this.items.increment(reservation.getProductId(), reservation.getQuantity()));
		this.reservations.deleteAllInBatch(held);
	}

}
