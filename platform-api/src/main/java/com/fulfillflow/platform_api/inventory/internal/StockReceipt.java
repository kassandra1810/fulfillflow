package com.fulfillflow.platform_api.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single intake of a product into a warehouse. It has its own id, which the
 * matching {@code RECEIPT} {@link StockMovement} refers back to (user story 19).
 */
@Entity
@Table(name = "stock_receipt")
class StockReceipt {

	@Id
	private UUID id;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "warehouse_id", nullable = false)
	private UUID warehouseId;

	@Column(nullable = false)
	private long quantity;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected StockReceipt() {
	}

	StockReceipt(UUID id, UUID productId, UUID warehouseId, long quantity, Instant createdAt) {
		this.id = id;
		this.productId = productId;
		this.warehouseId = warehouseId;
		this.quantity = quantity;
		this.createdAt = createdAt;
	}

	UUID getId() {
		return id;
	}

	UUID getProductId() {
		return productId;
	}

	long getQuantity() {
		return quantity;
	}
}
