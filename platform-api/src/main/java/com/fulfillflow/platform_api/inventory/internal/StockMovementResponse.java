package com.fulfillflow.platform_api.inventory.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a product's movement history as seen over the API: what happened,
 * how much, when, and which receipt caused it. The product is in the URL, not the
 * body, and the warehouse is never exposed (spec). {@code orderId} and the
 * {@code RESERVATION}/{@code RELEASE} types arrive with orders (T08).
 */
record StockMovementResponse(
		UUID id,
		MovementType type,
		long quantity,
		Instant createdAt,
		UUID stockReceiptId) {

	static StockMovementResponse from(StockMovement movement) {
		return new StockMovementResponse(
				movement.getId(),
				movement.getType(),
				movement.getQuantity(),
				movement.getCreatedAt(),
				movement.getStockReceiptId());
	}
}
