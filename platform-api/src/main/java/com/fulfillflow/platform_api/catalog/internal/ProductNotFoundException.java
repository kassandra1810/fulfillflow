package com.fulfillflow.platform_api.catalog.internal;

import java.util.UUID;

/**
 * Thrown when no product exists for a requested {@code id}. Mapped to HTTP 404
 * Not Found (Problem Details) by {@link ProductExceptionHandler}.
 */
class ProductNotFoundException extends RuntimeException {

	private final UUID id;

	ProductNotFoundException(UUID id) {
		super("Product with id '" + id + "' not found");
		this.id = id;
	}

	UUID getId() {
		return id;
	}
}
