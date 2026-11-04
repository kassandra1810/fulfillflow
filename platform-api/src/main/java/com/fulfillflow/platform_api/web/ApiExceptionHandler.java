package com.fulfillflow.platform_api.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place that renders API errors as RFC 9457 Problem Details, for
 * every module. Bean validation failures become 400 with a per-field
 * {@code errors} map. Modules signal domain errors (404, 409, ...) by throwing
 * an {@link org.springframework.web.ErrorResponseException} that carries its
 * own status and Problem Detail, so this package depends on no module.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		ProblemDetail body = validationFailed(errors);

		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (var result : ex.getParameterValidationResults()) {
			String parameter = result.getMethodParameter().getParameterName();
			for (var error : result.getResolvableErrors()) {
				errors.putIfAbsent(parameter, error.getDefaultMessage());
			}
		}
		return handleExceptionInternal(ex, validationFailed(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		Map<String, String> errors = Map.of(ex.getPropertyName(), "has an invalid format");
		return handleExceptionInternal(ex, validationFailed(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	private static ProblemDetail validationFailed(Map<String, String> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request validation failed");
		body.setTitle("Validation failed");
		body.setProperty("errors", errors);
		return body;
	}
}
