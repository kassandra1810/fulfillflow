package com.fulfillflow.platform_api.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

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
		ProblemDetail body = ValidationFailedException.body(errors);

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
		return handleExceptionInternal(ex, ValidationFailedException.body(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * A body whose JSON is well-formed but has a field of the wrong type (a number
	 * or boolean where a {@code String} is expected, a string where a number is
	 * expected, a fractional value for an integer field, ...) becomes a 400 with
	 * {@code errors.<field>}, like every other validation 400 — not a bare "could
	 * not read" message. Syntactically broken JSON, an empty body and a JSON root
	 * that is not an object keep the default 400 Problem Details without an
	 * {@code errors} map, because no single field is at fault.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		if (ex.getCause() instanceof MismatchedInputException mismatch) {
			String field = fieldPath(mismatch);
			if (field != null) {
				Map<String, String> errors = Map.of(field, "has an invalid type");
				return handleExceptionInternal(
						ex, ValidationFailedException.body(errors), headers, HttpStatus.BAD_REQUEST, request);
			}
		}
		return super.handleHttpMessageNotReadable(ex, headers, status, request);
	}

	/**
	 * The dotted path of the field that failed to bind (e.g. {@code price}, or
	 * {@code outer.inner} when nested), or {@code null} when the failure points at
	 * no field (a non-object JSON root, an empty body). Only property names
	 * contribute; array indices are skipped.
	 */
	private static String fieldPath(MismatchedInputException ex) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : ex.getPath()) {
			String name = reference.getPropertyName();
			if (name == null) {
				continue;
			}
			if (!path.isEmpty()) {
				path.append('.');
			}
			path.append(name);
		}
		return path.isEmpty() ? null : path.toString();
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		Map<String, String> errors = Map.of(ex.getPropertyName(), "has an invalid format");
		return handleExceptionInternal(ex, ValidationFailedException.body(errors), headers, HttpStatus.BAD_REQUEST, request);
	}
}
