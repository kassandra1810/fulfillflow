package com.fulfillflow.platform_api.catalog.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fulfillflow.platform_api.web.ValidationFailedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Body of {@code PATCH /api/v1/products/{id}}. PATCH semantics require telling an
 * omitted field (leave unchanged) apart from an explicit {@code null}
 * ({@code "description": null} clears the description; {@code "name": null} /
 * {@code "price": null} are a 400). Jackson calls a setter for an explicit
 * {@code null} but never for an absent field, so the {@code *Present} flags carry
 * that distinction.
 *
 * <p>A sent {@code name} or {@code price} is checked against the constraints
 * declared once on {@link CreateProductRequest} (via
 * {@link Validator#validateValue}), so both endpoints share one set of rules and
 * messages. Only the PATCH-specific rules ({@code sku} immutable, {@code version}
 * required) live here. All failures land in one {@code errors} map.
 */
class UpdateProductRequest {

	private boolean skuPresent;

	private boolean namePresent;

	private String name;

	private boolean descriptionPresent;

	private String description;

	private boolean pricePresent;

	private BigDecimal price;

	private Long version;

	// sku is immutable: its value is irrelevant, only its presence matters. Jackson
	// otherwise ignores unknown fields silently, so sku must be captured explicitly.
	@JsonProperty("sku")
	void setSku(Object sku) {
		this.skuPresent = true;
	}

	@JsonProperty("name")
	void setName(String name) {
		// Trimmed before validation, like POST (rule in ProductText).
		this.name = ProductText.strip(name);
		this.namePresent = true;
	}

	@JsonProperty("description")
	void setDescription(String description) {
		this.description = description;
		this.descriptionPresent = true;
	}

	@JsonProperty("price")
	void setPrice(BigDecimal price) {
		this.price = price;
		this.pricePresent = true;
	}

	@JsonProperty("version")
	void setVersion(Long version) {
		this.version = version;
	}

	Long version() {
		return version;
	}

	/**
	 * Validates the request, throwing a 400 carrying a per-field {@code errors} map
	 * if anything fails. Order mirrors the spec: all validation (including a missing
	 * {@code version} and an immutable {@code sku}) is a 400, before any 404/409.
	 */
	void validate(Validator validator) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (skuPresent) {
			errors.put("sku", "sku cannot be changed");
		}
		if (version == null) {
			errors.put("version", "must not be null");
		}
		if (namePresent) {
			rejectInvalid(validator, "name", name, errors);
		}
		if (pricePresent) {
			rejectInvalid(validator, "price", price, errors);
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
	}

	private static void rejectInvalid(Validator validator, String field, Object value, Map<String, String> errors) {
		for (ConstraintViolation<CreateProductRequest> violation
				: validator.validateValue(CreateProductRequest.class, field, value)) {
			errors.putIfAbsent(field, violation.getMessage());
		}
	}

	/** Applies the present fields to the product; call only after {@link #validate}. */
	void applyTo(Product product) {
		if (namePresent) {
			product.changeName(name);
		}
		if (descriptionPresent) {
			product.changeDescription(description);
		}
		if (pricePresent) {
			product.changePrice(price);
		}
	}
}
