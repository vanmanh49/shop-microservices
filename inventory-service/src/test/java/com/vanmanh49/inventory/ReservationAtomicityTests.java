package com.vanmanh49.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Not transactional on purpose: the rollback under test happens in the service's own
 * transaction, which a test-managed transaction would swallow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationAtomicityTests {

	private static final long PLENTIFUL = 9101;

	private static final long SCARCE = 9102;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private InventoryService inventory;

	@Autowired
	private ReservationRepository reservations;

	@AfterEach
	void cleanUp() {
		this.inventory.release("atomic-1");
		this.inventory.setStock(PLENTIFUL, 0);
		this.inventory.setStock(SCARCE, 0);
	}

	@Test
	void reserveIsAllOrNothing() throws Exception {
		this.inventory.setStock(PLENTIFUL, 5);
		this.inventory.setStock(SCARCE, 1);
		String body = """
				{"orderRef":"atomic-1","items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}"""
			.formatted(PLENTIFUL, SCARCE);

		this.mvc.perform(post("/api/inventory/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict());

		assertThat(this.inventory.get(PLENTIFUL).getAvailable()).isEqualTo(5);
		assertThat(this.inventory.get(SCARCE).getAvailable()).isEqualTo(1);
		assertThat(this.reservations.findByOrderRef("atomic-1")).isEmpty();
	}

}
