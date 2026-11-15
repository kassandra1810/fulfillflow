package com.fulfillflow.platform_api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class StockMovementIT extends AbstractPostgresIntegrationTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	/** The single warehouse, inserted by the V4 migration. */
	private static final UUID DEFAULT_WAREHOUSE_ID =
			UUID.fromString("11111111-1111-7111-8111-111111111111");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void listsReceiptMovementsNewestFirstWithTheirReceiptIds() throws Exception {
		String productId = createProduct("SKU-MOVES-ORDER");
		String firstReceipt = receive(productId, 3);
		String secondReceipt = receive(productId, 5);
		String thirdReceipt = receive(productId, 7);

		MvcResult result = mockMvc.perform(get(movementsPath(productId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andReturn();
		String json = result.getResponse().getContentAsString();

		// Newest first: createdAt descending (ties would then fall back to id).
		List<String> createdAts = JsonPath.read(json, "$.content[*].createdAt");
		List<Instant> times = createdAts.stream().map(Instant::parse).toList();
		assertThat(times).isSortedAccordingTo(Comparator.reverseOrder());

		// Every entry is a RECEIPT pointing at the receipt that caused it, with its
		// quantity; the most recent receipt is at the top.
		assertThat((List<String>) JsonPath.read(json, "$.content[*].type")).containsOnly("RECEIPT");
		assertThat((String) JsonPath.read(json, "$.content[0].stockReceiptId")).isEqualTo(thirdReceipt);
		assertThat(quantityOfReceipt(json, firstReceipt)).isEqualTo(3);
		assertThat(quantityOfReceipt(json, secondReceipt)).isEqualTo(5);
		assertThat(quantityOfReceipt(json, thirdReceipt)).isEqualTo(7);
	}

	@Test
	void entryExposesOnlyTypeQuantityCreatedAtAndReceipt() throws Exception {
		String productId = createProduct("SKU-MOVES-FIELDS");
		String receiptId = receive(productId, 4);

		mockMvc.perform(get(movementsPath(productId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").isNotEmpty())
				.andExpect(jsonPath("$.content[0].type").value("RECEIPT"))
				.andExpect(jsonPath("$.content[0].quantity").value(4))
				.andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.content[0].stockReceiptId").value(receiptId))
				// The warehouse is never exposed; the product is in the URL; orderId
				// and the reservation types arrive with orders (T08).
				.andExpect(jsonPath("$.content[0].warehouseId").doesNotExist())
				.andExpect(jsonPath("$.content[0].productId").doesNotExist())
				.andExpect(jsonPath("$.content[0].orderId").doesNotExist());
	}

	@Test
	void breaksTiesOnEqualCreatedAtByIdDescending() throws Exception {
		String productId = createProduct("SKU-MOVES-TIE");
		UUID receiptId = UUID.fromString("018f8e2a-0000-7000-8000-0000000000a0");
		Instant sameInstant = Instant.parse("2026-11-15T10:15:30.123456Z");
		insertReceipt(receiptId, productId, 2, sameInstant);

		// Two movements with an identical created_at — only possible to force over
		// JDBC, never over HTTP. Insert the smaller id last to prove ordering is by
		// id, not insertion order.
		UUID smallerId = UUID.fromString("018f8e2a-0000-7000-8000-0000000000aa");
		UUID largerId = UUID.fromString("018f8e2a-0000-7000-8000-0000000000bb");
		insertMovement(largerId, productId, 2, sameInstant, receiptId);
		insertMovement(smallerId, productId, 2, sameInstant, receiptId);

		mockMvc.perform(get(movementsPath(productId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].id").value(largerId.toString()))
				.andExpect(jsonPath("$.content[1].id").value(smallerId.toString()));
	}

	@Test
	void paginatesThroughSeveralPagesAsAStableWindow() throws Exception {
		String productId = createProduct("SKU-MOVES-PAGES");
		for (int i = 0; i < 25; i++) {
			receive(productId, 1);
		}

		List<String> page0 = movementIdsOnPage(productId, 0, 10);
		List<String> page1 = movementIdsOnPage(productId, 1, 10);
		List<String> page2 = movementIdsOnPage(productId, 2, 10);
		List<String> firstTwentyInOneRequest = movementIdsOnPage(productId, 0, 20);

		assertThat(page0).hasSize(10);
		assertThat(page1).hasSize(10);
		assertThat(page2).hasSize(5);

		List<String> combined = new ArrayList<>(page0);
		combined.addAll(page1);
		assertThat(combined).isEqualTo(firstTwentyInOneRequest);

		mockMvc.perform(get(movementsPath(productId)).param("page", "0").param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(25))
				.andExpect(jsonPath("$.page.totalPages").value(3))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.size").value(10));
	}

	@Test
	void productWithNoMovementsReturnsEmptyPage() throws Exception {
		String productId = createProduct("SKU-MOVES-EMPTY");

		mockMvc.perform(get(movementsPath(productId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(0))
				.andExpect(jsonPath("$.page.size").value(20));
	}

	@Test
	void unknownProductIs404() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000d070");

		mockMvc.perform(get(movementsPath(unknown.toString())))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void malformedProductIdIs400() throws Exception {
		mockMvc.perform(get("/api/v1/products/not-a-uuid/stock-movements"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.id").exists());
	}

	@Test
	void rejectsNegativePageWith400() throws Exception {
		String productId = createProduct("SKU-MOVES-BAD-PAGE");

		mockMvc.perform(get(movementsPath(productId)).param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void rejectsSizeBelowOneWith400() throws Exception {
		String productId = createProduct("SKU-MOVES-SMALL-SIZE");

		mockMvc.perform(get(movementsPath(productId)).param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.size").exists());
	}

	@Test
	void rejectsSizeAbove100With400() throws Exception {
		String productId = createProduct("SKU-MOVES-BIG-SIZE");

		mockMvc.perform(get(movementsPath(productId)).param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.size").exists());
	}

	@Test
	void rejectsNonNumericPageWith400() throws Exception {
		String productId = createProduct("SKU-MOVES-NAN-PAGE");

		mockMvc.perform(get(movementsPath(productId)).param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void rejectsNonNumericSizeWith400() throws Exception {
		String productId = createProduct("SKU-MOVES-NAN-SIZE");

		mockMvc.perform(get(movementsPath(productId)).param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.size").exists());
	}

	@Test
	void rejectsPageWhoseOffsetDoesNotFitTheDatabaseWith400() throws Exception {
		String productId = createProduct("SKU-MOVES-HUGE-PAGE");

		mockMvc.perform(get(movementsPath(productId)).param("page", "30000000").param("size", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void doesNotAllowDeletingHistory() throws Exception {
		String productId = createProduct("SKU-MOVES-NO-DELETE");

		mockMvc.perform(delete(movementsPath(productId)))
				.andExpect(status().isMethodNotAllowed());
	}

	private String movementsPath(String productId) {
		return "/api/v1/products/" + productId + "/stock-movements";
	}

	private List<String> movementIdsOnPage(String productId, int page, int size) throws Exception {
		MvcResult result = mockMvc.perform(get(movementsPath(productId))
						.param("page", String.valueOf(page))
						.param("size", String.valueOf(size)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].id");
	}

	private int quantityOfReceipt(String json, String receiptId) {
		List<Object> quantities = JsonPath.read(json,
				"$.content[?(@.stockReceiptId == '" + receiptId + "')].quantity");
		return ((Number) quantities.get(0)).intValue();
	}

	private void insertReceipt(UUID id, String productId, long quantity, Instant createdAt) {
		jdbc.update("INSERT INTO stock_receipt (id, product_id, warehouse_id, quantity, created_at) "
						+ "VALUES (?, ?, ?, ?, ?)",
				id, UUID.fromString(productId), DEFAULT_WAREHOUSE_ID, quantity, Timestamp.from(createdAt));
	}

	private void insertMovement(UUID id, String productId, long quantity, Instant createdAt, UUID receiptId) {
		jdbc.update("INSERT INTO stock_movement "
						+ "(id, type, product_id, warehouse_id, quantity, created_at, stock_receipt_id) "
						+ "VALUES (?, 'RECEIPT', ?, ?, ?, ?, ?)",
				id, UUID.fromString(productId), DEFAULT_WAREHOUSE_ID, quantity,
				Timestamp.from(createdAt), receiptId);
	}

	private String receive(String productId, int quantity) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/stock-receipts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"productId":"%s","quantity":%d}
								""".formatted(productId, quantity)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
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
}
