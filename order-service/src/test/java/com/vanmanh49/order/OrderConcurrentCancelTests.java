package com.vanmanh49.order;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.vanmanh49.order.client.InventoryClient;
import com.vanmanh49.order.client.ProductClient;
import com.vanmanh49.order.event.OrderEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Two cancels of one order that overlap must not both succeed. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderConcurrentCancelTests {

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

	@AfterEach
	void cleanUp() {
		this.orders.deleteAll();
	}

	@Test
	void secondWriterOfTheSameOrderVersionIsRejected() {
		Order saved = this.orders.saveAndFlush(newOrder());
		// Two requests each load the order while it is still CONFIRMED...
		Order first = this.orders.findByIdAndUserId(saved.getId(), "42").orElseThrow();
		Order second = this.orders.findByIdAndUserId(saved.getId(), "42").orElseThrow();

		first.cancel();
		this.orders.saveAndFlush(first);

		// ...so the slower one must be told its copy is out of date.
		second.cancel();
		assertThatExceptionOfType(ObjectOptimisticLockingFailureException.class)
			.isThrownBy(() -> this.orders.saveAndFlush(second));
	}

	@Test
	void losingAConcurrentCancelReturns409() throws Exception {
		Order saved = this.orders.saveAndFlush(newOrder());
		willThrow(new ObjectOptimisticLockingFailureException(Order.class, saved.getId())).given(this.orderStore)
			.markCancelled(anyLong(), anyString());

		this.mvc.perform(post("/api/orders/" + saved.getId() + "/cancel").header("X-User-Id", "42"))
			.andExpect(status().isConflict());
	}

	private static Order newOrder() {
		return new Order(UUID.randomUUID(), "42",
				List.of(new OrderItem(1L, "Keyboard", new BigDecimal("49.99"), 2)));
	}

}
