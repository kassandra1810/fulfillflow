package com.fulfillflow.platform_api.catalog.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test (no database) covering validation and error mapping to Problem
 * Details for {@link ProductController}. Runs under {@code ./mvnw test}.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ProductService service;

	@Test
	void rejectsEmptyNameWith400AndFieldPointer() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-1","name":"","price":19.90}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void rejectsMissingSkuWith400AndFieldPointer() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Widget","price":19.90}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.sku").exists());
	}

	@Test
	void rejectsZeroPriceWith400AndFieldPointer() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-1","name":"Widget","price":0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void rejectsNegativePriceWith400AndFieldPointer() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-1","name":"Widget","price":-1.00}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void rejectsPriceWithMoreThanTwoDecimalsWith400AndFieldPointer() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-1","name":"Widget","price":1.999}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void mapsDuplicateSkuTo409ProblemDetail() throws Exception {
		when(service.create(eq("SKU-1"), any(), any(), any()))
				.thenThrow(new DuplicateSkuException("SKU-1"));

		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-1","name":"Widget","price":19.90}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void mapsUnknownIdTo404ProblemDetail() throws Exception {
		UUID id = UUID.fromString("018f8e2a-0000-7000-8000-000000000000");
		when(service.findById(id)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/products/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").exists());
	}
}
