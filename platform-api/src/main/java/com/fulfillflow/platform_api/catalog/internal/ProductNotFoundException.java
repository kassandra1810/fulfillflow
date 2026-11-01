package com.fulfillflow.platform_api.catalog.internal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when no product exists for a requested {@code id}. Carries its own
 * 404 Problem Detail, rendered by the shared web error handling.
 */
class ProductNotFoundException extends ErrorResponseException {

	ProductNotFoundException(UUID id) {
		super(HttpStatus.NOT_FOUND, problem(id), null);
	}

	private static ProblemDetail problem(UUID id) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.NOT_FOUND, "Product with id '" + id + "' not found");
		body.setTitle("Product not found");
		return body;
	}
}
