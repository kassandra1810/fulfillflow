package com.fulfillflow.platform_api.catalog.internal;

import java.math.BigDecimal;

public record CreateProductRequest(String sku, String name, String description, BigDecimal price) {
}
