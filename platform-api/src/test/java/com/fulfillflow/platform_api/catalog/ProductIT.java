package com.fulfillflow.platform_api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ProductIT extends AbstractPostgresIntegrationTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

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
		assertThat(created.getResponse().getContentAsString())
				.contains("\"price\":19.90");

		String id = readId(created);

		MvcResult fetched = mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(fetched.getResponse().getContentAsString())
				.contains("\"price\":19.90");
	}

	@Test
	void returnsPriceWithScale2FromPostAndGetForFewerDecimals() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-SCALE","name":"One decimal","price":19.9}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		// POST and GET must return identical data: 19.90, not 19.9 then 19.90.
		assertThat(created.getResponse().getContentAsString()).contains("\"price\":19.90");

		MvcResult fetched = mockMvc.perform(get("/api/v1/products/{id}", readId(created)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(fetched.getResponse().getContentAsString()).contains("\"price\":19.90");
	}

	@Test
	void assignsUuidV7Ids() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-UUID","name":"Identified","price":1.00}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		UUID id = UUID.fromString(readId(created));
		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void databaseRejectsNonPositivePriceAsSecondLineOfDefence() {
		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO product (id, sku, name, price, version) VALUES (?, ?, ?, ?, 0)",
				UUID.randomUUID(), "SKU-DB-ZERO", "Zero in DB", BigDecimal.ZERO))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(productCount("SKU-DB-ZERO")).isZero();
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

	@Test
	void rejectsEmptyNameWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-EMPTY-NAME","name":"","price":19.90}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-EMPTY-NAME")).isZero();
	}

	@Test
	void rejectsMissingSkuWith400AndAddsNothing() throws Exception {
		long before = totalProducts();

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"No sku","price":19.90}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());

		assertThat(totalProducts()).isEqualTo(before);
	}

	@Test
	void rejectsZeroPriceWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-ZERO","name":"Zero priced","price":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());

		assertThat(productCount("SKU-ZERO")).isZero();
	}

	@Test
	void rejectsNegativePriceWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-NEG","name":"Negative priced","price":-1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());

		assertThat(productCount("SKU-NEG")).isZero();
	}

	@Test
	void rejectsPriceWithMoreThanTwoDecimalsWith400AndNoSilentRounding() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-3DP","name":"Three decimals","price":1.999}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());

		// No silent rounding: nothing was stored.
		assertThat(productCount("SKU-3DP")).isZero();
	}

	@Test
	void rejectsDuplicateSkuWith409AndKeepsOnlyOne() throws Exception {
		String payload = """
				{"sku":"SKU-DUP","name":"Original","price":9.99}
				""";
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-DUP","name":"Duplicate","price":12.00}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(productCount("SKU-DUP")).isEqualTo(1);
	}

	@Test
	void returns404ProblemDetailForUnknownId() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-000000000000");

		mockMvc.perform(get("/api/v1/products/{id}", unknown))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void concurrentCreatesWithSameSkuGiveOne201AndOnly409s() throws Exception {
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				statuses.add(executor.submit(() -> {
					start.await();
					return mockMvc.perform(post("/api/v1/products")
									.contentType(MediaType.APPLICATION_JSON)
									.content("""
											{"sku":"SKU-RACE","name":"Raced","price":3.00}
											"""))
							.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();

			List<Integer> results = new ArrayList<>();
			for (Future<Integer> status : statuses) {
				results.add(status.get());
			}
			assertThat(results).containsOnly(201, 409);
			assertThat(results).filteredOn(code -> code == 201).hasSize(1);
		} finally {
			executor.shutdownNow();
		}
		assertThat(productCount("SKU-RACE")).isEqualTo(1);
	}

	@Test
	void trimsSkuSoPaddedDuplicateIsRejectedWith409() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"  SKU-TRIM  ","name":"Padded","price":4.00}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sku").value("SKU-TRIM"));

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-TRIM","name":"Plain","price":4.00}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.sku").value("SKU-TRIM"));

		assertThat(productCount("SKU-TRIM")).isEqualTo(1);
	}

	@Test
	void treatsSkuCaseSensitively() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-CASE","name":"Upper","price":2.00}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"sku-case","name":"Lower","price":2.00}
								"""))
				.andExpect(status().isCreated());
	}

	@Test
	void rejectsSkuLongerThan64With400AndAddsNothing() throws Exception {
		String sku = "S".repeat(65);

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"%s","name":"Long sku","price":1.00}
								""".formatted(sku)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());

		assertThat(productCount(sku)).isZero();
	}

	@Test
	void rejectsNameLongerThan255With400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-LONG-NAME","name":"%s","price":1.00}
								""".formatted("N".repeat(256))))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-LONG-NAME")).isZero();
	}

	@Test
	void rejectsMissingNameWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-NO-NAME","price":1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-NO-NAME")).isZero();
	}

	private long totalProducts() {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM product", Long.class);
		return count == null ? 0 : count;
	}

	private long productCount(String sku) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM product WHERE sku = ?", Long.class, sku);
		return count == null ? 0 : count;
	}

	private String readId(MvcResult result) {
		String location = result.getResponse().getHeader("Location");
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
