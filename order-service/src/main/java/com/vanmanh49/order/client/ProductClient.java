package com.vanmanh49.order.client;

import java.util.Optional;

import com.vanmanh49.order.client.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

	private static final String SERVICE = "product-service";

	private final RestClient restClient;

	private final CircuitBreaker circuitBreaker;

	public ProductClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> circuitBreakers,
			@Value("${shop.clients.product.base-url}") String baseUrl) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.circuitBreaker = circuitBreakers.create("product");
	}

	public ProductDto get(Long id) {
		Optional<ProductDto> product = this.circuitBreaker.run(() -> fetch(id), (failure) -> {
			throw new DependencyUnavailableException(SERVICE, failure);
		});
		return product.orElseThrow(() -> new ProductNotFoundException(id));
	}

	// A 404 is a business answer, not a failure: it is turned into a value inside the
	// circuit breaker so that it never counts towards opening the circuit.
	private Optional<ProductDto> fetch(Long id) {
		try {
			return Optional
				.ofNullable(this.restClient.get().uri("/api/products/{id}", id).retrieve().body(ProductDto.class));
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

}
