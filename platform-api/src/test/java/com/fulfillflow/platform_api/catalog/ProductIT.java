package com.fulfillflow.platform_api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
	void rejectsPriceSentAsJsonStringWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-STRING-PRICE","name":"String priced","price":"19.90"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());

		assertThat(productCount("SKU-STRING-PRICE")).isZero();
	}

	@Test
	void rejectsNumberForNameWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-NUM-NAME","name":123,"price":1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-NUM-NAME")).isZero();
	}

	@Test
	void rejectsNumberForSkuWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":123,"name":"Numbered sku","price":1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());

		assertThat(productCount("123")).isZero();
	}

	@Test
	void rejectsBooleanForDescriptionWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-BOOL-DESC","name":"Bool desc","description":true,"price":1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.description").exists());

		assertThat(productCount("SKU-BOOL-DESC")).isZero();
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
	void trimsNameSoSurroundingWhitespaceIsNotStored() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-TRIM-NAME","name":"  Widget  ","price":1.00}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Widget"))
				.andReturn();

		mockMvc.perform(get("/api/v1/products/{id}", readId(created)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget"));
	}

	@Test
	void acceptsNameOf255CharactersAfterTrimmingSurroundingWhitespace() throws Exception {
		String name = "N".repeat(255);

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-NAME-255","name":"  %s  ","price":1.00}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(name));
	}

	@Test
	void rejectsNameOf256AfterTrimmingWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-NAME-256","name":"  %s  ","price":1.00}
								""".formatted("N".repeat(256))))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-NAME-256")).isZero();
	}

	@Test
	void rejectsWhitespaceOnlyNameWith400AndAddsNothing() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-WS-NAME","name":"   ","price":1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(productCount("SKU-WS-NAME")).isZero();
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

	@Test
	void listReturnsDefaultPageNumberAndSizeWithMetadata() throws Exception {
		createProduct("SKU-LIST-DEFAULT");

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").isNumber())
				.andExpect(jsonPath("$.page.totalPages").isNumber());
	}

	@Test
	void listKeepsSkuAscendingOrderAcrossPages() throws Exception {
		// A family sharing a unique, smallest-sorting prefix so these products sort
		// first (before every SKU-/sku- product) and their only differing part is a
		// numeric suffix, giving an unambiguous order in any collation.
		String prefix = "0PG-" + UUID.randomUUID() + "-";
		List<String> created = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			String sku = prefix + String.format("%02d", i);
			createProduct(sku);
			created.add(sku);
		}

		List<String> firstPage = skusOnPage(0, 10);
		List<String> secondPage = skusOnPage(1, 10);
		List<String> firstTwentyInOneRequest = skusOnPage(0, 20);

		// Pagination is a stable window over one total order: page 0 then page 1
		// equals the first 20 in a single request.
		List<String> combined = new ArrayList<>(firstPage);
		combined.addAll(secondPage);
		assertThat(firstPage).hasSize(10);
		assertThat(secondPage).hasSize(10);
		assertThat(combined).isEqualTo(firstTwentyInOneRequest);

		// Order is by sku ascending: the smallest 20 SKUs are this family's 01..20.
		assertThat(firstTwentyInOneRequest).isEqualTo(created.subList(0, 20));
	}

	@Test
	void listRejectsNegativePageWith400() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void listRejectsSizeBelowOneWith400() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.size").exists());
	}

	@Test
	void listRejectsSizeAbove100With400() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.size").exists());
	}

	@Test
	void listRejectsNonNumericPageWith400() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void listRejectsPageWhoseOffsetDoesNotFitTheDatabaseWith400() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("page", "30000000").param("size", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.page").exists());
	}

	@Test
	void getRejectsMalformedIdWith400() throws Exception {
		mockMvc.perform(get("/api/v1/products/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.id").exists());
	}

	@Test
	void listBySkuReturnsSingleElementPageForExistingSku() throws Exception {
		String sku = "SKU-FILTER-HIT-" + UUID.randomUUID();
		createProduct(sku);

		mockMvc.perform(get("/api/v1/products").param("sku", sku))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sku").value(sku))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void listBySkuReturnsEmptyPageNeverNotFoundForUnknownSku() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("sku", "SKU-NO-SUCH-" + UUID.randomUUID()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void listBySkuStripsSurroundingWhitespaceLikeCreate() throws Exception {
		String sku = "SKU-FILTER-STRIP-" + UUID.randomUUID();
		createProduct(sku);

		mockMvc.perform(get("/api/v1/products").param("sku", "  " + sku + " "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sku").value(sku));
	}

	@Test
	void listByBlankSkuReturnsEmptyPage() throws Exception {
		createProduct("SKU-FILTER-BLANK-" + UUID.randomUUID());

		mockMvc.perform(get("/api/v1/products").param("sku", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void patchUpdatesNameOnlyLeavesOtherFieldsAndBumpsVersion() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NAME", "Old name", "Old desc", "7.50");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New name","version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New name"))
				.andExpect(jsonPath("$.description").value("Old desc"))
				.andExpect(jsonPath("$.price").value(7.50))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New name"))
				.andExpect(jsonPath("$.description").value("Old desc"))
				.andExpect(jsonPath("$.price").value(7.50))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void patchUpdatesDescriptionOnly() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-DESC", "Name", "Old desc", "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":"New desc","version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Name"))
				.andExpect(jsonPath("$.description").value("New desc"))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void patchUpdatesPriceOnlyNormalizedToScale2() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-PRICE", "Name", null, "1.00");

		MvcResult patched = mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"price":19.9,"version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andReturn();

		// Same scale-2 normalization as create: 19.90, never 19.9.
		assertThat(patched.getResponse().getContentAsString()).contains("\"price\":19.90");
	}

	@Test
	void patchClearsDescriptionWithExplicitNull() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-CLEAR", "Name", "Has a description", "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":null,"version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value(nullValue()))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value(nullValue()));
	}

	@Test
	void patchWithNoEffectiveChangeKeepsVersion() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NOOP", "Name", "Desc", "1.00");

		// Only the version, no field to change: nothing is dirty, so the version
		// must stay put (no forced increment) — no concurrent change is lost.
		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0));

		// Re-sending the same values is likewise a no-op: still version 0.
		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Name","description":"Desc","price":1.00,"version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithSkuInBodyReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-SKU", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-OTHER","name":"New","version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.sku").value("SKU-PATCH-SKU"))
				.andExpect(jsonPath("$.name").value("Name"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithoutVersionReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NOVER", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.version").exists());
	}

	@Test
	void patchWithNullNameReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NULLNAME", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":null,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void patchWithBlankNameReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-BLANKNAME", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"   ","version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void patchWithNameLongerThan255Returns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-LONGNAME", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","version":0}
								""".formatted("N".repeat(256))))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void patchTrimsNameSoSurroundingWhitespaceIsNotStored() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-TRIMNAME", "Old name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"  Widget  ","version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget"));
	}

	@Test
	void patchAcceptsNameOf255AfterTrimmingSurroundingWhitespace() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NAME255", "Name", null, "1.00");
		String name = "N".repeat(255);

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"  %s  ","version":0}
								""".formatted(name)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(name))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void patchWithNameTrimmingToStoredValueKeepsVersion() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-SAMENAME", "Name", null, "1.00");

		// " Name " strips to "Name", the stored value, so nothing is dirty and the
		// version stays put — same no-change rule as a re-sent identical field.
		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":" Name ","version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Name"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithNullPriceReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NULLPRICE", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"price":null,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void patchWithNonPositivePriceReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-ZEROPRICE", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"price":0,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void patchWithPriceOverTwoDecimalsReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-3DP", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"price":1.999,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void patchWithStaleVersionReturns409() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-STALE", "Name", null, "1.00");

		// First edit on version 0 succeeds and bumps to 1.
		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"First","version":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));

		// Second edit still on version 0 is a lost update → 409.
		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Second","version":0}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());

		// The losing edit changed nothing.
		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.name").value("First"))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void patchUnknownProductReturns404() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000aaaa");

		mockMvc.perform(patch("/api/v1/products/{id}", unknown)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New","version":0}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void patchWithNullSkuReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NULLSKU", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":null,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());
	}

	@Test
	void patchWithNullVersionReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NULLVER", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New","version":null}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.version").exists());
	}

	@Test
	void patchWithStringPriceReturns400WithErrorsPrice() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-STRINGPRICE", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"price":"abc","version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithNumberForNameReturns400AndKeepsVersion() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NUMNAME", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":123,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.name").value("Name"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithNumberForDescriptionReturns400AndKeepsVersion() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-NUMDESC", "Name", "Desc", "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":123,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.description").exists());

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.description").value("Desc"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void patchWithVersionAsStringReturns400() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-VERSTR", "Name", null, "1.00");

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New","version":"0"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.version").exists());
	}

	@Test
	void patchValidationFailsBeforeUnknownProductIsReported() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000bbbb");

		mockMvc.perform(patch("/api/v1/products/{id}", unknown)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.version").exists());
	}

	@Test
	void patchUnknownProductIsReportedBeforeVersionConflict() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-00000000cccc");

		mockMvc.perform(patch("/api/v1/products/{id}", unknown)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New","version":42}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void patchValidationMessagesMatchCreate() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-MESSAGES", "Name", null, "1.00");
		String createErrors = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-PATCH-MESSAGES-2","name":null,"price":null}
								"""))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(patch("/api/v1/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":null,"price":null,"version":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").value(JsonPath.<String>read(createErrors, "$.errors.name")))
				.andExpect(jsonPath("$.errors.price").value(JsonPath.<String>read(createErrors, "$.errors.price")));
	}

	@Test
	void concurrentPatchesWithSameVersionGiveOne200AndRest409AndBumpVersionOnce() throws Exception {
		String id = createDetailedProduct("SKU-PATCH-RACE", "Original", null, "1.00");

		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				int n = i;
				statuses.add(executor.submit(() -> {
					start.await();
					return mockMvc.perform(patch("/api/v1/products/{id}", id)
									.contentType(MediaType.APPLICATION_JSON)
									.content("""
											{"name":"Updated-%d","version":0}
											""".formatted(n)))
							.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();

			List<Integer> results = new ArrayList<>();
			for (Future<Integer> status : statuses) {
				results.add(status.get());
			}
			assertThat(results).containsOnly(200, 409);
			assertThat(results).filteredOn(code -> code == 200).hasSize(1);
		} finally {
			executor.shutdownNow();
		}

		// Exactly one edit won, so the version is the old one plus one.
		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(jsonPath("$.version").value(1));
	}

	private String createDetailedProduct(String sku, String name, String description, String price)
			throws Exception {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		MvcResult created = mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"%s","name":"%s","description":%s,"price":%s}
								""".formatted(sku, name, descriptionJson, price)))
				.andExpect(status().isCreated())
				.andReturn();
		return readId(created);
	}

	private List<String> skusOnPage(int page, int size) throws Exception {
		String json = mockMvc.perform(get("/api/v1/products")
						.param("page", String.valueOf(page))
						.param("size", String.valueOf(size)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.content[*].sku");
	}

	private void createProduct(String sku) throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"%s","name":"Listed","price":1.00}
								""".formatted(sku)))
				.andExpect(status().isCreated());
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
