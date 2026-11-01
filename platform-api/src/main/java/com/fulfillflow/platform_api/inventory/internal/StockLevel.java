package com.fulfillflow.platform_api.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The stock of one product in one warehouse. A row exists only once the product
 * has been received at least once; until then the product's stock reads as all
 * zero. The {@code on_hand}/{@code reserved} invariant is guarded in the
 * database ({@code ck_stock_level_reserved}).
 */
@Entity
@Table(name = "stock_level")
class StockLevel {

	@Id
	private UUID id;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "warehouse_id", nullable = false)
	private UUID warehouseId;

	@Column(name = "on_hand", nullable = false)
	private long onHand;

	@Column(nullable = false)
	private long reserved;

	protected StockLevel() {
	}

	private StockLevel(UUID id, UUID productId, UUID warehouseId, long onHand, long reserved) {
		this.id = id;
		this.productId = productId;
		this.warehouseId = warehouseId;
		this.onHand = onHand;
		this.reserved = reserved;
	}

	/** The row created by a product's first receipt: {@code onHand} units, none reserved. */
	static StockLevel firstReceipt(UUID id, UUID productId, UUID warehouseId, long onHand) {
		return new StockLevel(id, productId, warehouseId, onHand, 0);
	}

	long getOnHand() {
		return onHand;
	}

	long getReserved() {
		return reserved;
	}

	long getAvailable() {
		return onHand - reserved;
	}
}
