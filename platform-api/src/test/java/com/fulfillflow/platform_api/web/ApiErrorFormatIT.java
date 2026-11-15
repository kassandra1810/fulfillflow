package com.fulfillflow.platform_api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiErrorFormatIT extends AbstractPostgresIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void wrongFieldTypeIs400ProblemDetailPointingAtTheField() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-BAD-TYPE","name":"Bad","price":"abc"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void malformedJsonIs400ProblemDetailWithoutErrors() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sku\":"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {"[]", "\"str\"", "null", ""})
	void bodyWithNoFieldAtFaultIs400ProblemDetailWithoutErrors(String body) throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void numberOutOfRangeIs400PointingAtTheField() throws Exception {
		mockMvc.perform(post("/api/v1/stock-receipts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"productId":"018f8e2a-0000-7000-8000-00000000dddd","quantity":99999999999999999999}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.errors.quantity").exists());
	}
}
