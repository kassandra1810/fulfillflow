package com.fulfillflow.platform_api.catalog.internal;

import java.math.BigDecimal;
import java.util.UUID;

record ProductResponse(
		UUID id,
		String sku,
		String name,
		String description,
		BigDecimal price,
		long version) {

	static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getSku(),
				product.getName(),
				product.getDescription(),
				product.getPrice(),
				product.getVersion());
	}
}
