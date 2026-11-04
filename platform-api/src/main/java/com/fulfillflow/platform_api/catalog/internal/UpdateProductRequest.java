package com.fulfillflow.platform_api.catalog.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
 * <p>Value constraints ({@code @Size}, {@code @Positive}, {@code @Digits}) match
 * {@link CreateProductRequest} and run through the shared {@link Validator}; the
 * PATCH-specific presence, {@code null} and {@code sku}-immutability rules are
 * checked in {@link #validate}. All failures land in one {@code errors} map.
 */
class UpdateProductRequest {

	private boolean skuPresent;

	private boolean namePresent;

	@Size(max = 255)
	private String name;

	private boolean descriptionPresent;

	private String description;

	private boolean pricePresent;

	@Positive
	@Digits(integer = 17, fraction = 2)
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
		this.name = name;
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
		if (namePresent && (name == null || name.isBlank())) {
			errors.put("name", "must not be blank");
		}
		if (pricePresent && price == null) {
			errors.put("price", "must not be null");
		}
		for (ConstraintViolation<UpdateProductRequest> violation : validator.validate(this)) {
			errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
		}
		if (!errors.isEmpty()) {
			throw new ProductValidationException(errors);
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
