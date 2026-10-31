package com.fulfillflow.platform_api.catalog;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

		org.assertj.core.api.Assertions.assertThat(productCount("SKU-EMPTY-NAME")).isZero();
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

		org.assertj.core.api.Assertions.assertThat(totalProducts()).isEqualTo(before);
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

		org.assertj.core.api.Assertions.assertThat(productCount("SKU-ZERO")).isZero();
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

		org.assertj.core.api.Assertions.assertThat(productCount("SKU-NEG")).isZero();
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
		org.assertj.core.api.Assertions.assertThat(productCount("SKU-3DP")).isZero();
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

		org.assertj.core.api.Assertions.assertThat(productCount("SKU-DUP")).isEqualTo(1);
	}

	@Test
	void returns404ProblemDetailForUnknownId() throws Exception {
		UUID unknown = UUID.fromString("018f8e2a-0000-7000-8000-000000000000");

		mockMvc.perform(get("/api/v1/products/{id}", unknown))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
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
