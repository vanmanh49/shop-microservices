package com.vanmanh49.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import com.vanmanh49.order.client.dto.ProductDto;
import com.vanmanh49.order.client.dto.ReservationItem;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientErrorMappingTests {

	private static final String PRODUCT_JSON = """
			{"id":1,"sku":"SKU-1","name":"Keyboard","description":"Mechanical","price":49.99}""";

	private MockRestServiceServer server;

	private ProductClient productClient;

	private InventoryClient inventoryClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
		properties.setDisableTimeLimiter(true);
		Resilience4JCircuitBreakerFactory circuitBreakers = new Resilience4JCircuitBreakerFactory(
				CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null, properties);
		ClientConfig.circuitBreakerDefaults().customize(circuitBreakers);
		this.productClient = new ProductClient(builder, circuitBreakers, "http://product-service");
		this.inventoryClient = new InventoryClient(builder, circuitBreakers, "http://inventory-service");
	}

	@Test
	void productIsReadFromCatalogResponse() {
		this.server.expect(requestTo("http://product-service/api/products/1"))
			.andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));

		assertThat(this.productClient.get(1L)).isEqualTo(new ProductDto(1L, "Keyboard", new BigDecimal("49.99")));
	}

	@Test
	void product404MapsToProductNotFound() {
		this.server.expect(requestTo("http://product-service/api/products/7"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatExceptionOfType(ProductNotFoundException.class).isThrownBy(() -> this.productClient.get(7L));
	}

	@Test
	void product500MapsToDependencyUnavailable() {
		this.server.expect(requestTo("http://product-service/api/products/1"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatExceptionOfType(DependencyUnavailableException.class).isThrownBy(() -> this.productClient.get(1L));
	}

	@Test
	void connectionFailureMapsToDependencyUnavailable() {
		this.server.expect(requestTo("http://product-service/api/products/1"))
			.andRespond(withException(new IOException("connection refused")));

		assertThatExceptionOfType(DependencyUnavailableException.class).isThrownBy(() -> this.productClient.get(1L));
	}

	@Test
	void reserveSendsOrderRefAndItems() {
		this.server.expect(requestTo("http://inventory-service/api/inventory/reservations"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
					{"orderRef":"ref-1","items":[{"productId":1,"quantity":2}]}"""))
			.andRespond(withStatus(HttpStatus.CREATED));

		this.inventoryClient.reserve("ref-1", List.of(new ReservationItem(1L, 2)));

		this.server.verify();
	}

	@Test
	void inventory409MapsToInsufficientStock() {
		this.server.expect(requestTo("http://inventory-service/api/inventory/reservations"))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatExceptionOfType(InsufficientStockException.class)
			.isThrownBy(() -> this.inventoryClient.reserve("ref-1", List.of(new ReservationItem(1L, 2))));
	}

	@Test
	void inventory500MapsToDependencyUnavailable() {
		this.server.expect(requestTo("http://inventory-service/api/inventory/reservations"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatExceptionOfType(DependencyUnavailableException.class)
			.isThrownBy(() -> this.inventoryClient.reserve("ref-1", List.of(new ReservationItem(1L, 2))));
	}

	@Test
	void releaseSendsDelete() {
		this.server.expect(requestTo("http://inventory-service/api/inventory/reservations/ref-1"))
			.andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.inventoryClient.release("ref-1");

		this.server.verify();
	}

	@Test
	void repeatedNotFoundDoesNotOpenCircuit() {
		this.server.expect(ExpectedCount.times(10), requestTo("http://product-service/api/products/7"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		this.server.expect(requestTo("http://product-service/api/products/1"))
			.andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));

		for (int i = 0; i < 10; i++) {
			assertThatExceptionOfType(ProductNotFoundException.class).isThrownBy(() -> this.productClient.get(7L));
		}

		assertThat(this.productClient.get(1L).name()).isEqualTo("Keyboard");
	}

	@Test
	void circuitOpensAfterRepeatedFailuresAndStopsCallingTheService() {
		this.server.expect(ExpectedCount.times(5), requestTo("http://product-service/api/products/1"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		for (int i = 0; i < 6; i++) {
			assertThatExceptionOfType(DependencyUnavailableException.class)
				.isThrownBy(() -> this.productClient.get(1L));
		}

		// The sixth call was rejected by the open circuit without reaching the server.
		this.server.verify();
	}

}
