package com.fulfillflow.platform_api.inventory.internal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a receipt or stock read names a product that does not exist in the
 * catalog. Carries its own 404 Problem Detail, rendered by the shared web error
 * handling. Inventory does not know catalog's own not-found exception (it lives
 * in catalog's internal package), so it signals the same outcome with its own.
 */
class UnknownProductException extends ErrorResponseException {

	UnknownProductException(UUID productId) {
		super(HttpStatus.NOT_FOUND, problem(productId), null);
	}

	private static ProblemDetail problem(UUID productId) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.NOT_FOUND, "Product with id '" + productId + "' not found");
		body.setTitle("Product not found");
		return body;
	}
}
