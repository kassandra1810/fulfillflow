package com.fulfillflow.platform_api.catalog.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates catalog errors into RFC 9457 Problem Details responses. Bean
 * validation failures become 400 with a per-field {@code errors} map; a
 * duplicate {@code sku} becomes 409 and an unknown product id becomes 404.
 */
@RestControllerAdvice
class ProductExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request validation failed");
		body.setTitle("Validation failed");

		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		body.setProperty("errors", errors);

		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(DuplicateSkuException.class)
	ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		body.setTitle("Duplicate sku");
		body.setProperty("sku", ex.getSku());
		return body;
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		body.setTitle("Product not found");
		return body;
	}
}
