package com.vanmanh49.inventory.web;

import com.vanmanh49.inventory.InventoryService;
import com.vanmanh49.inventory.web.dto.ReservationRequest;
import com.vanmanh49.inventory.web.dto.StockRequest;
import com.vanmanh49.inventory.web.dto.StockResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping("/{productId}")
	public StockResponse get(@PathVariable Long productId) {
		return StockResponse.from(this.inventoryService.get(productId));
	}

	@PutMapping("/{productId}")
	public StockResponse setStock(@PathVariable Long productId, @Valid @RequestBody StockRequest request) {
		return StockResponse.from(this.inventoryService.setStock(productId, request.quantity()));
	}

	// The reservation endpoints are internal: the gateway does not expose them.
	@PostMapping("/reservations")
	@ResponseStatus(HttpStatus.CREATED)
	public void reserve(@Valid @RequestBody ReservationRequest request) {
		this.inventoryService.reserve(request);
	}

	@DeleteMapping("/reservations/{orderRef}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void release(@PathVariable String orderRef) {
		this.inventoryService.release(orderRef);
	}

}
