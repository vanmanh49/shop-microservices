package com.vanmanh49.order.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.vanmanh49.order.client.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

	private static final String SERVICE = "product-service";

	private final RestClient restClient;

	private final CircuitBreaker circuitBreaker;

	public ProductClient(@LoadBalanced RestClient.Builder builder, CircuitBreakerFactory<?, ?> circuitBreakers,
			@Value("${shop.clients.product.base-url}") String baseUrl) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.circuitBreaker = circuitBreakers.create("product");
	}

	/**
	 * Fetches all products in one call. A product the catalog does not have is a business
	 * answer, not a failure: it is checked outside the circuit breaker so that it never
	 * counts towards opening the circuit.
	 */
	public Map<Long, ProductDto> getAll(Collection<Long> ids) {
		ProductDto[] found = this.circuitBreaker.run(() -> this.restClient.get()
			.uri((uri) -> uri.path("/api/products").queryParam("ids", ids.toArray()).build())
			.retrieve()
			.body(ProductDto[].class), (failure) -> {
				throw new DependencyUnavailableException(SERVICE, failure);
			});
		Map<Long, ProductDto> byId = new HashMap<>();
		for (ProductDto product : found) {
			byId.put(product.id(), product);
		}
		for (Long id : ids) {
			if (!byId.containsKey(id)) {
				throw new ProductNotFoundException(id);
			}
		}
		return byId;
	}

}
