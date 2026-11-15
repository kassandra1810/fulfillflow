package com.fulfillflow.platform_api.catalog.internal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
		@NotBlank @Size(max = 64) String sku,
		@NotBlank @Size(max = 255) String name,
		String description,
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {

	public CreateProductRequest {
		// Leading/trailing whitespace is part of neither the sku nor the name (spec),
		// so "SKU-1 " is the same product as "SKU-1" and "  Widget  " stores as
		// "Widget". Stripping before @Size/@NotBlank run checks the trimmed value.
		// Case is significant. description is free text and is not trimmed.
		sku = ProductText.strip(sku);
		name = ProductText.strip(name);
	}
}
