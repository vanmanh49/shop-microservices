package com.vanmanh49.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ReservationRepository reservations;

	@Test
	void putCreatesThenReplacesStock() throws Exception {
		setStock(1, 5).andExpect(status().isOk())
			.andExpect(jsonPath("$.productId").value(1))
			.andExpect(jsonPath("$.available").value(5));

		setStock(1, 2).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(2));
		expectAvailable(1, 2);
	}

	@Test
	void putNegativeReturns400() throws Exception {
		setStock(1, -1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantity").exists());
	}

	@Test
	void getUnknownReturns404() throws Exception {
		this.mvc.perform(get("/api/inventory/999")).andExpect(status().isNotFound());
	}

	@Test
	void reserveDecrementsStock() throws Exception {
		setStock(1, 5);

		reserve("o-1", "{\"productId\":1,\"quantity\":3}").andExpect(status().isCreated());

		expectAvailable(1, 2);
	}

	@Test
	void reserveUnknownProductReturns409() throws Exception {
		reserve("o-1", "{\"productId\":999,\"quantity\":1}").andExpect(status().isConflict());
	}

	@Test
	void reserveSameOrderRefTwiceIsIdempotent() throws Exception {
		setStock(1, 5);
		reserve("o-1", "{\"productId\":1,\"quantity\":3}").andExpect(status().isCreated());

		reserve("o-1", "{\"productId\":1,\"quantity\":3}").andExpect(status().isCreated());

		expectAvailable(1, 2);
	}

	@Test
	void reserveMergesDuplicateProductLines() throws Exception {
		setStock(1, 5);

		reserve("o-1", "{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":2}")
			.andExpect(status().isCreated());

		expectAvailable(1, 2);
		assertThat(this.reservations.findByOrderRef("o-1")).hasSize(1);
	}

	@Test
	void reserveZeroQuantityReturns400() throws Exception {
		setStock(1, 5);

		reserve("o-1", "{\"productId\":1,\"quantity\":0}").andExpect(status().isBadRequest());

		expectAvailable(1, 5);
	}

	@Test
	void releaseRestoresStock() throws Exception {
		setStock(1, 5);
		reserve("o-1", "{\"productId\":1,\"quantity\":3}");

		this.mvc.perform(delete("/api/inventory/reservations/o-1")).andExpect(status().isNoContent());

		expectAvailable(1, 5);
		assertThat(this.reservations.findByOrderRef("o-1")).isEmpty();
	}

	@Test
	void releaseUnknownOrderRefReturns204() throws Exception {
		this.mvc.perform(delete("/api/inventory/reservations/nope")).andExpect(status().isNoContent());
	}

	private ResultActions setStock(long productId, int quantity) throws Exception {
		return this.mvc.perform(put("/api/inventory/" + productId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantity\":" + quantity + "}"));
	}

	private ResultActions reserve(String orderRef, String items) throws Exception {
		return this.mvc.perform(post("/api/inventory/reservations").contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderRef\":\"" + orderRef + "\",\"items\":[" + items + "]}"));
	}

	private void expectAvailable(long productId, int available) throws Exception {
		this.mvc.perform(get("/api/inventory/" + productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(available));
	}

}
