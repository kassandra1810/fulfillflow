package com.fulfillflow.platform_api.catalog.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a product is created with a {@code sku} that already exists.
 * Carries its own 409 Problem Detail, rendered by the shared web error handling.
 */
class DuplicateSkuException extends ErrorResponseException {

	DuplicateSkuException(String sku) {
		super(HttpStatus.CONFLICT, problem(sku), null);
	}

	private static ProblemDetail problem(String sku) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "Product with sku '" + sku + "' already exists");
		body.setTitle("Duplicate sku");
		body.setProperty("sku", sku);
		return body;
	}
}
