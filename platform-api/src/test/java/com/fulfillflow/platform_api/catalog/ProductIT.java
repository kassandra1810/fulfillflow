package com.fulfillflow.platform_api.catalog;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ProductIT extends AbstractPostgresIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void createsProductThenReadsItBackById() throws Exception {
		String payload = """
				{"sku":"SKU-001","name":"Widget","description":"A nice widget","price":19.90}
				""";

		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.sku").value("SKU-001"))
				.andExpect(jsonPath("$.name").value("Widget"))
				.andExpect(jsonPath("$.description").value("A nice widget"))
				.andExpect(jsonPath("$.price").value(19.90))
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();

		String id = readId(created);

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.sku").value("SKU-001"))
				.andExpect(jsonPath("$.name").value("Widget"))
				.andExpect(jsonPath("$.description").value("A nice widget"))
				.andExpect(jsonPath("$.price").value(19.90))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void keepsTwoDecimalPriceUnchanged() throws Exception {
		String payload = """
				{"sku":"SKU-PRICE","name":"Priced","price":19.90}
				""";

		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andReturn();

		// Exactly two decimal places, trailing zero preserved (not 19.9).
		org.assertj.core.api.Assertions.assertThat(created.getResponse().getContentAsString())
				.contains("\"price\":19.90");

		String id = readId(created);

		MvcResult fetched = mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andReturn();

		org.assertj.core.api.Assertions.assertThat(fetched.getResponse().getContentAsString())
				.contains("\"price\":19.90");
	}

	@Test
	void storesNullDescriptionWhenOmitted() throws Exception {
		String payload = """
				{"sku":"SKU-NODESC","name":"No description","price":5.00}
				""";

		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value(nullValue()))
				.andReturn();

		String id = readId(created);

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value(nullValue()));
	}

	private String readId(MvcResult result) {
		String location = result.getResponse().getHeader("Location");
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
