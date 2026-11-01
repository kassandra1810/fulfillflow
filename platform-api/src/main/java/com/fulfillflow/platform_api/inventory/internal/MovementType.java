package com.fulfillflow.platform_api.inventory.internal;

/**
 * The kind of a {@link StockMovement}, from the domain glossary (PRD 4). Only
 * {@code RECEIPT} exists so far; {@code RESERVATION} and {@code RELEASE} are
 * added with orders (T08, T11). Stored as text, matching the {@code ck_stock_movement_type} check.
 */
enum MovementType {
	RECEIPT
}
