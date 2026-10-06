package com.vanmanh49.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vanmanh49.notification.event.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private NotificationService notifications;

	@Autowired
	private NotificationRepository repository;

	@Test
	void orderPlacedIsStored() {
		this.notifications.handle(event("ORDER_PLACED", 7L, "42", "99.98"));

		List<Notification> stored = this.repository.findAll();
		assertThat(stored).hasSize(1);
		Notification notification = stored.get(0);
		assertThat(notification.getUserId()).isEqualTo("42");
		assertThat(notification.getOrderId()).isEqualTo(7L);
		assertThat(notification.getType()).isEqualTo("ORDER_PLACED");
		assertThat(notification.getMessage()).contains("7").contains("99.98").contains("confirmed");
	}

	@Test
	void orderCancelledIsStored() {
		this.notifications.handle(event("ORDER_CANCELLED", 7L, "42", "99.98"));

		assertThat(this.repository.findAll()).singleElement()
			.satisfies((notification) -> assertThat(notification.getMessage()).contains("7").contains("cancelled"));
	}

	@Test
	void duplicateEventIsIgnored() {
		OrderEvent event = event("ORDER_PLACED", 7L, "42", "99.98");
		this.notifications.handle(event);

		this.notifications.handle(event);

		assertThat(this.repository.count()).isEqualTo(1);
	}

	@Test
	void listReturnsOnlyCallersNotificationsNewestFirst() throws Exception {
		this.notifications.handle(event("ORDER_PLACED", 7L, "42", "99.98"));
		this.notifications.handle(event("ORDER_PLACED", 8L, "43", "10.00"));
		this.notifications.handle(event("ORDER_CANCELLED", 7L, "42", "99.98"));

		this.mvc.perform(get("/api/notifications").header("X-User-Id", "42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].type").value("ORDER_CANCELLED"))
			.andExpect(jsonPath("$[1].type").value("ORDER_PLACED"))
			.andExpect(jsonPath("$[0].orderId").value(7));
	}

	@Test
	void listWithoutIdentityHeaderReturns401() throws Exception {
		this.mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
	}

	private static OrderEvent event(String type, Long orderId, String userId, String total) {
		return new OrderEvent(UUID.randomUUID(), type, orderId, userId, new BigDecimal(total), Instant.now());
	}

}
