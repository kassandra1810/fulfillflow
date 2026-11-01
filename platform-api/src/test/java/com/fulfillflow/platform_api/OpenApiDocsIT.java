package com.fulfillflow.platform_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI document is served (developer story 56) and covers the product
 * endpoints, matching the address documented in the README.
 */
@AutoConfigureMockMvc
class OpenApiDocsIT extends AbstractPostgresIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void servesOpenApiDocumentCoveringProductEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.paths['/api/v1/products']").exists());
	}
}
