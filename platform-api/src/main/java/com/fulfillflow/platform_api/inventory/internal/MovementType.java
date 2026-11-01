package com.fulfillflow.platform_api.inventory.internal;

/**
 * The kind of a {@link StockMovement}, from the domain glossary (PRD 4). Only
 * {@code RECEIPT} is written in T06; reservations and releases arrive with
 * orders. Stored as text, matching the {@code ck_stock_movement_type} check.
 */
enum MovementType {
	RECEIPT,
	RESERVATION,
	RELEASE
}
