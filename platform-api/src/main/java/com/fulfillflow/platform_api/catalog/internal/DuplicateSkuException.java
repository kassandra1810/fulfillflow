package com.fulfillflow.platform_api.catalog.internal;

/**
 * Thrown when a product is created with a {@code sku} that already exists.
 * Mapped to HTTP 409 Conflict (Problem Details) by {@link ProductExceptionHandler}.
 */
class DuplicateSkuException extends RuntimeException {

	private final String sku;

	DuplicateSkuException(String sku) {
		super("Product with sku '" + sku + "' already exists");
		this.sku = sku;
	}

	String getSku() {
		return sku;
	}
}
