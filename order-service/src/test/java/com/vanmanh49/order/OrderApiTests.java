package com.vanmanh49.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.vanmanh49.order.client.DependencyUnavailableException;
import com.vanmanh49.order.client.InsufficientStockException;
import com.vanmanh49.order.client.InventoryClient;
import com.vanmanh49.order.client.ProductClient;
import com.vanmanh49.order.client.ProductNotFoundException;
import com.vanmanh49.order.client.dto.ProductDto;
import com.vanmanh49.order.client.dto.ReservationItem;
import com.vanmanh49.order.event.OrderEvent;
import com.vanmanh49.order.event.OrderEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Not transactional: order events are published after commit, which never happens inside a
 * test-managed transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private OrderRepository orders;

	@MockitoBean
	private ProductClient productClient;

	@MockitoBean
	private InventoryClient inventoryClient;

	@MockitoBean
	private OrderEventPublisher publisher;

	@MockitoSpyBean
	private OrderStore orderStore;

	@BeforeEach
	void stubCatalog() {
		given(this.productClient.get(1L)).willReturn(new ProductDto(1L, "Keyboard", new BigDecimal("49.99")));
		given(this.productClient.get(2L)).willReturn(new ProductDto(2L, "Mouse", new BigDecimal("19.50")));
	}

	@AfterEach
	void cleanUp() {
		this.orders.deleteAll();
	}

	@Test
	void placeOrderReturns201AndPublishesEvent() throws Exception {
		String body = place("42", "{\"productId\":1,\"quantity\":2}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.total").value(99.98))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
			.andExpect(jsonPath("$.items[0].unitPrice").value(49.99))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number orderId = JsonPath.read(body, "$.id");
		String orderRef = JsonPath.read(body, "$.orderRef");

		verify(this.inventoryClient).reserve(orderRef, List.of(new ReservationItem(1L, 2)));
		OrderEvent event = publishedEvent();
		assertThat(event.eventId()).isNotNull();
		assertThat(event.type()).isEqualTo("ORDER_PLACED");
		assertThat(event.orderId()).isEqualTo(orderId.longValue());
		assertThat(event.userId()).isEqualTo("42");
		assertThat(event.total()).isEqualByComparingTo("99.98");
		assertThat(event.occurredAt()).isNotNull();
	}

	@Test
	void totalSumsAllLines() throws Exception {
		place("42", "{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.total").value(119.48));
	}

	@Test
	void duplicateProductLinesAreMerged() throws Exception {
		place("42", "{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":2}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].quantity").value(3));

		verify(this.inventoryClient).reserve(anyString(), eq(List.of(new ReservationItem(1L, 3))));
	}

	@Test
	void unknownProductReturns422AndReservesNothing() throws Exception {
		given(this.productClient.get(999L)).willThrow(new ProductNotFoundException(999L));

		place("42", "{\"productId\":999,\"quantity\":1}").andExpect(status().isUnprocessableEntity());

		verify(this.inventoryClient, never()).reserve(anyString(), anyList());
		assertThat(this.orders.count()).isZero();
	}

	@Test
	void insufficientStockReturns409AndSavesNothing() throws Exception {
		willThrow(new InsufficientStockException()).given(this.inventoryClient).reserve(anyString(), anyList());

		place("42", "{\"productId\":1,\"quantity\":99}").andExpect(status().isConflict());

		assertThat(this.orders.count()).isZero();
		verify(this.publisher, never()).publish(any());
	}

	@Test
	void productServiceDownReturns503AndReservesNothing() throws Exception {
		given(this.productClient.get(1L)).willThrow(new DependencyUnavailableException("product-service", null));

		place("42", "{\"productId\":1,\"quantity\":1}").andExpect(status().isServiceUnavailable());

		verify(this.inventoryClient, never()).reserve(anyString(), anyList());
		assertThat(this.orders.count()).isZero();
	}

	@Test
	void inventoryServiceDownReturns503AndLeavesNoReservation() throws Exception {
		willThrow(new DependencyUnavailableException("inventory-service", null)).given(this.inventoryClient)
			.reserve(anyString(), anyList());

		place("42", "{\"productId\":1,\"quantity\":1}").andExpect(status().isServiceUnavailable());

		// The reserve call may have reached inventory before failing, so it is released.
		ArgumentCaptor<String> orderRef = ArgumentCaptor.forClass(String.class);
		verify(this.inventoryClient).reserve(orderRef.capture(), anyList());
		verify(this.inventoryClient).release(orderRef.getValue());
		assertThat(this.orders.count()).isZero();
	}

	@Test
	void saveFailureReleasesReservation() throws Exception {
		willThrow(new IllegalStateException("database unavailable")).given(this.orderStore)
			.saveConfirmed(any(), anyString(), anyList());

		assertThatThrownBy(() -> place("42", "{\"productId\":1,\"quantity\":2}"))
			.hasRootCauseMessage("database unavailable");

		ArgumentCaptor<String> orderRef = ArgumentCaptor.forClass(String.class);
		verify(this.inventoryClient).reserve(orderRef.capture(), anyList());
		verify(this.inventoryClient).release(orderRef.getValue());
		verify(this.publisher, never()).publish(any());
	}

	@Test
	void emptyItemsReturns400() throws Exception {
		place("42", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.items").exists());
	}

	@Test
	void zeroQuantityReturns400() throws Exception {
		place("42", "{\"productId\":1,\"quantity\":0}").andExpect(status().isBadRequest());

		verify(this.inventoryClient, never()).reserve(anyString(), anyList());
	}

	@Test
	void missingUserHeaderReturns401() throws Exception {
		this.mvc
			.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
			.andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void listReturnsOnlyCallersOrders() throws Exception {
		placeAndGetId("42");
		placeAndGetId("42");
		placeAndGetId("43");

		this.mvc.perform(get("/api/orders").header("X-User-Id", "42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].items[0].productName").value("Keyboard"));
	}

	@Test
	void getOtherUsersOrderReturns404() throws Exception {
		long id = placeAndGetId("42");

		this.mvc.perform(get("/api/orders/" + id).header("X-User-Id", "42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));
		this.mvc.perform(get("/api/orders/" + id).header("X-User-Id", "43")).andExpect(status().isNotFound());
		this.mvc.perform(get("/api/orders/999999").header("X-User-Id", "42")).andExpect(status().isNotFound());
	}

	@Test
	void cancelReleasesStockAndPublishesEvent() throws Exception {
		String body = place("42", "{\"productId\":1,\"quantity\":2}").andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(body, "$.id");
		String orderRef = JsonPath.read(body, "$.orderRef");

		this.mvc.perform(post("/api/orders/" + id + "/cancel").header("X-User-Id", "42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));

		verify(this.inventoryClient).release(orderRef);
		ArgumentCaptor<OrderEvent> events = ArgumentCaptor.forClass(OrderEvent.class);
		verify(this.publisher, org.mockito.Mockito.times(2)).publish(events.capture());
		assertThat(events.getAllValues()).extracting(OrderEvent::type)
			.containsExactly("ORDER_PLACED", "ORDER_CANCELLED");
	}

	@Test
	void cancelTwiceReturns409() throws Exception {
		long id = placeAndGetId("42");
		this.mvc.perform(post("/api/orders/" + id + "/cancel").header("X-User-Id", "42"))
			.andExpect(status().isOk());

		this.mvc.perform(post("/api/orders/" + id + "/cancel").header("X-User-Id", "42"))
			.andExpect(status().isConflict());
	}

	@Test
	void cancelOtherUsersOrderReturns404() throws Exception {
		long id = placeAndGetId("42");

		this.mvc.perform(post("/api/orders/" + id + "/cancel").header("X-User-Id", "43"))
			.andExpect(status().isNotFound());

		verify(this.inventoryClient, never()).release(anyString());
	}

	private ResultActions place(String userId, String items) throws Exception {
		return this.mvc.perform(post("/api/orders").header("X-User-Id", userId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\":[" + items + "]}"));
	}

	private long placeAndGetId(String userId) throws Exception {
		String body = place(userId, "{\"productId\":1,\"quantity\":1}").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number id = JsonPath.read(body, "$.id");
		return id.longValue();
	}

	private OrderEvent publishedEvent() {
		ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
		verify(this.publisher).publish(event.capture());
		return event.getValue();
	}

}
