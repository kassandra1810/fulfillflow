package com.fulfillflow.platform_api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillflow.platform_api.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiErrorFormatIT extends AbstractPostgresIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void unreadableBodyIs400ProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku":"SKU-BAD-JSON","name":"Bad","price":"abc"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400));
	}
}
