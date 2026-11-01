package com.fulfillflow.platform_api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@AutoConfigureMockMvc
class StockIT extends AbstractPostgresIntegrationTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	MockMvc mockMvc;

	@Test
	void receivesStockThenReadsItBackThroughStockLevel() throws Exception {
		String productId = createProduct("SKU-RECV");

		mockMvc.perform(receipt(productId, 7))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.productId").value(productId))
				.andExpect(jsonPath("$.quantity").value(7));

		mockMvc.perform(get("/api/v1/products/{id}/stock", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHand").value(7))
				.andExpect(jsonPath("$.reserved").value(0))
				.andExpect(jsonPath("$.available").value(7));
	}

	@Test
	void assignsUuidV7IdToReceipt() throws Exception {
		String productId = createProduct("SKU-RECV-UUID");

		MvcResult created = mockMvc.perform(receipt(productId, 1))
				.andExpect(status().isCreated())
				.andReturn();

		UUID id = UUID.fromString(readJsonId(created));
		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void sumsOnHandAcrossReceiptsAndAvailableEqualsOnHand() throws Exception {
		String productId = createProduct("SKU-SUM");

		mockMvc.perform(receipt(productId, 4)).andExpect(status().isCreated());
		mockMvc.perform(receipt(productId, 6)).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/products/{id}/stock", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHand").value(10))
				.andExpect(jsonPath("$.reserved").value(0))
				.andExpect(jsonPath("$.available").value(10));
	}

	@Test
	void rejectsZeroQuantityWith400() throws Exception {
		String productId = createProduct("SKU-ZERO-QTY");

		mockMvc.perform(receipt(productId, 0))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.quantity").exists());

		expectNoStock(productId);
	}

	@Test
	void rejectsNegativeQuantityWith400() throws Exception {
		String productId = createProduct("SKU-NEG-QTY");

		mockMvc.perform(receipt(productId, -3))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.quantity").exists());

		expectNoStock(productId);
	}

	@Test
	void rejectsFractionalQuantityWith400() throws Exception {
		String productId = createProduct("SKU-FRAC-QTY");

		mockMvc.perform(post("/api/v1/stock-receipts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"productId":"%s","quantity":2.5}
								""".formatted(productId)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		expectNoStock(productId);
	}

	@Test
	void rejectsMissingProductIdWith400() throws Exception {
		mockMvc.perform(post("/api/v1/stock-receipts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"quantity":1}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.productId").exists());
	}

	@Test
	void rejectsMissingQuantityWith400() throws Exception {
		String productId = createProduct("SKU-NO-QTY");

		mockMvc.perform(post("/api/v1/stock-receipts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"productId":"%s"}
								""".formatted(productId)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.quantity").exists());
	}

	@Test
	void receiptForUnknownProductIs404() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000d006");

		mockMvc.perform(receipt(unknown.toString(), 1))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void stockForUnknownProductIs404() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000d007");

		mockMvc.perform(get("/api/v1/products/{id}/stock", unknown))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void stockOfNeverReceivedProductIsAllZero() throws Exception {
		String productId = createProduct("SKU-UNTOUCHED");

		mockMvc.perform(get("/api/v1/products/{id}/stock", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHand").value(0))
				.andExpect(jsonPath("$.reserved").value(0))
				.andExpect(jsonPath("$.available").value(0));
	}

	private void expectNoStock(String productId) throws Exception {
		// A rejected receipt must not create a stock level: it still reads 0/0/0.
		mockMvc.perform(get("/api/v1/products/{id}/stock", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHand").value(0));
	}

	private RequestBuilder receipt(String productId, int quantity) {
		return post("/api/v1/stock-receipts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":"%s","quantity":%d}
						""".formatted(productId, quantity));
	}

	private String createProduct(String sku) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"%s","name":"Named","price":1.00}
								""".formatted(sku)))
				.andExpect(status().isCreated())
				.andReturn();
		String location = created.getResponse().getHeader("Location");
		return location.substring(location.lastIndexOf('/') + 1);
	}

	private String readJsonId(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}
}
