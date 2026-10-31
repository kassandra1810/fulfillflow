package com.fulfillflow.platform_api.catalog.internal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateProductRequest(
		@NotBlank String sku,
		@NotBlank String name,
		String description,
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {
}
