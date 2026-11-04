package com.fulfillflow.platform_api.catalog.internal;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a {@code PATCH} body fails validation (immutable {@code sku},
 * missing {@code version}, {@code null}/blank {@code name}, bad {@code price},
 * ...). Carries its own 400 Problem Detail with the per-field {@code errors} map,
 * the same shape the shared web error handling renders for bean validation.
 */
class ProductValidationException extends ErrorResponseException {

	ProductValidationException(Map<String, String> errors) {
		super(HttpStatus.BAD_REQUEST, problem(errors), null);
	}

	private static ProblemDetail problem(Map<String, String> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request validation failed");
		body.setTitle("Validation failed");
		body.setProperty("errors", errors);
		return body;
	}
}
