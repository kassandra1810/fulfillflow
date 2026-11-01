package com.fulfillflow.platform_api.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The minimal product data other modules need: {@code id}, {@code sku} and
 * {@code price}. A simple cross-module record, never a catalog entity (PRD 5.5).
 */
public record ProductSummary(UUID id, String sku, BigDecimal price) {
}
