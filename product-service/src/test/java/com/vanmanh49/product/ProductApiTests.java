package com.vanmanh49.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void createReturns201WithLocation() throws Exception {
		MvcResult result = create("SKU-1", "Keyboard", "49.99").andExpect(status().isCreated())
			.andExpect(jsonPath("$.sku").value("SKU-1"))
			.andExpect(jsonPath("$.name").value("Keyboard"))
			.andExpect(jsonPath("$.description").value("Mechanical"))
			.andExpect(jsonPath("$.price").value(49.99))
			.andReturn();

		Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/products/" + id);
	}

	@Test
	void createDuplicateSkuReturns409() throws Exception {
		create("SKU-1", "Keyboard", "49.99").andExpect(status().isCreated());

		create("SKU-1", "Other keyboard", "59.99").andExpect(status().isConflict());
	}

	@Test
	void createInvalidReturns400() throws Exception {
		create("SKU-1", " ", "0").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void createWithThreeDecimalPriceReturns400() throws Exception {
		create("SKU-1", "Keyboard", "49.999").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void getMissingReturns404() throws Exception {
		this.mvc.perform(get("/api/products/999")).andExpect(status().isNotFound());
	}

	@Test
	void updateMissingReturns404() throws Exception {
		this.mvc.perform(put("/api/products/999").contentType(MediaType.APPLICATION_JSON)
			.content(json("SKU-1", "Keyboard", "49.99"))).andExpect(status().isNotFound());
	}

	@Test
	void deleteMissingReturns404() throws Exception {
		this.mvc.perform(delete("/api/products/999")).andExpect(status().isNotFound());
	}

	@Test
	void updateChangesFields() throws Exception {
		long id = createAndGetId("SKU-1", "Keyboard", "49.99");

		this.mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(json("SKU-1B", "Keyboard v2", "59.00")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sku").value("SKU-1B"))
			.andExpect(jsonPath("$.name").value("Keyboard v2"))
			.andExpect(jsonPath("$.price").value(59.00));
		this.mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.name").value("Keyboard v2"));
	}

	@Test
	void updateToAnotherProductsSkuReturns409() throws Exception {
		createAndGetId("SKU-1", "Keyboard", "49.99");
		long id = createAndGetId("SKU-2", "Mouse", "19.50");

		this.mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(json("SKU-1", "Mouse", "19.50"))).andExpect(status().isConflict());
	}

	@Test
	void deleteReturns204() throws Exception {
		long id = createAndGetId("SKU-1", "Keyboard", "49.99");

		this.mvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void listIsPagedAndPageSizeIsCapped() throws Exception {
		createAndGetId("SKU-1", "Keyboard", "49.99");
		createAndGetId("SKU-2", "Mouse", "19.50");
		createAndGetId("SKU-3", "Monitor", "199.00");

		this.mvc.perform(get("/api/products?page=0&size=2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].sku").value("SKU-1"))
			.andExpect(jsonPath("$.page.totalElements").value(3));
		this.mvc.perform(get("/api/products?size=100000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100));
	}

	@Test
	void listWithNegativePageReturns400() throws Exception {
		this.mvc.perform(get("/api/products?page=-1")).andExpect(status().isBadRequest());
	}

	private ResultActions create(String sku, String name, String price) throws Exception {
		return this.mvc
			.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json(sku, name, price)));
	}

	private long createAndGetId(String sku, String name, String price) throws Exception {
		String body = create(sku, name, price).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number id = JsonPath.read(body, "$.id");
		return id.longValue();
	}

	private static String json(String sku, String name, String price) {
		return """
				{"sku":"%s","name":"%s","description":"Mechanical","price":%s}""".formatted(sku, name, price);
	}

}
