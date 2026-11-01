package com.fulfillflow.platform_api.inventory.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/stock-receipts}. {@code quantity} must be a
 * positive integer; a non-integer value (e.g. {@code 2.5}) fails to bind to an
 * {@code Integer} and is rejected as 400, never silently truncated (Jackson's
 * accept-float-as-int is disabled, see application.properties).
 */
public record StockReceiptRequest(
		@NotNull UUID productId,
		@NotNull @Positive Integer quantity) {
}
