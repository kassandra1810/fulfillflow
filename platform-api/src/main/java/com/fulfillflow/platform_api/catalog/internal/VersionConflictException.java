package com.fulfillflow.platform_api.catalog.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a {@code PATCH} is based on a stale {@code version}: either the
 * version in the request already differs from the stored one, or a concurrent
 * edit wins the optimistic lock at flush. Carries its own 409 Problem Detail,
 * rendered by the shared web error handling.
 */
class VersionConflictException extends ErrorResponseException {

	VersionConflictException() {
		super(HttpStatus.CONFLICT, problem(), null);
	}

	private static ProblemDetail problem() {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "Product was modified by another request; reload and retry");
		body.setTitle("Version conflict");
		return body;
	}
}
