package com.fulfillflow.platform_api.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A 400 for validation that cannot run through {@code @Valid} (e.g. PATCH rules
 * that depend on which fields were sent). Modules throw it with a per-field map;
 * it renders the same Problem Detail as every other validation 400, built only
 * here, so the error shape lives in one place.
 */
public class ValidationFailedException extends ErrorResponseException {

	public ValidationFailedException(Map<String, String> errors) {
		super(HttpStatus.BAD_REQUEST, body(errors), null);
	}

	static ProblemDetail body(Map<String, String> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request validation failed");
		body.setTitle("Validation failed");
		body.setProperty("errors", errors);
		return body;
	}
}
