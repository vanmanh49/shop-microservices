package com.vanmanh49.order.client;

import java.util.List;

import com.vanmanh49.order.client.dto.ReservationItem;
import com.vanmanh49.order.client.dto.ReservationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

	private static final String SERVICE = "inventory-service";

	private final RestClient restClient;

	private final CircuitBreaker circuitBreaker;

	public InventoryClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> circuitBreakers,
			@Value("${shop.clients.inventory.base-url}") String baseUrl) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.circuitBreaker = circuitBreakers.create("inventory");
	}

	/** Reserves all items or none. */
	public void reserve(String orderRef, List<ReservationItem> items) {
		boolean reserved = this.circuitBreaker.run(() -> tryReserve(new ReservationRequest(orderRef, items)),
				(failure) -> {
					throw new DependencyUnavailableException(SERVICE, failure);
				});
		if (!reserved) {
			throw new InsufficientStockException();
		}
	}

	/** Returns the stock reserved under the order reference; a no-op if there is none. */
	public void release(String orderRef) {
		this.circuitBreaker.run(() -> this.restClient.delete()
			.uri("/api/inventory/reservations/{orderRef}", orderRef)
			.retrieve()
			.toBodilessEntity(), (failure) -> {
				throw new DependencyUnavailableException(SERVICE, failure);
			});
	}

	// A 409 means "not enough stock", which must not count as a dependency failure.
	private boolean tryReserve(ReservationRequest request) {
		try {
			this.restClient.post().uri("/api/inventory/reservations").body(request).retrieve().toBodilessEntity();
			return true;
		}
		catch (HttpClientErrorException.Conflict ex) {
			return false;
		}
	}

}
