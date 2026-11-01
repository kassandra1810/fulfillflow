package com.fulfillflow.platform_api.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only entry in a product's movement history: what happened, to how
 * much, when, and which source caused it. The stock always equals the sums of
 * these entries (user story 26). Never updated or deleted.
 */
@Entity
@Table(name = "stock_movement")
class StockMovement {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MovementType type;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "warehouse_id", nullable = false)
	private UUID warehouseId;

	@Column(nullable = false)
	private long quantity;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "stock_receipt_id")
	private UUID stockReceiptId;

	protected StockMovement() {
	}

	private StockMovement(UUID id, MovementType type, UUID productId, UUID warehouseId,
			long quantity, Instant createdAt, UUID stockReceiptId) {
		this.id = id;
		this.type = type;
		this.productId = productId;
		this.warehouseId = warehouseId;
		this.quantity = quantity;
		this.createdAt = createdAt;
		this.stockReceiptId = stockReceiptId;
	}

	/** A {@code RECEIPT} entry caused by the given receipt. */
	static StockMovement receipt(UUID id, UUID productId, UUID warehouseId, long quantity,
			Instant createdAt, UUID stockReceiptId) {
		return new StockMovement(id, MovementType.RECEIPT, productId, warehouseId,
				quantity, createdAt, stockReceiptId);
	}
}
