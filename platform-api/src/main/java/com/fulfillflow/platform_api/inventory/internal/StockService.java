package com.fulfillflow.platform_api.inventory.internal;

import com.fulfillflow.platform_api.catalog.CatalogApi;
import com.fulfillflow.platform_api.shared.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving stock and reading the current stock of a product. These operations
 * are internal to the inventory module: no other module calls them, so they are
 * not part of {@link CatalogApi}-style public interfaces (spec "Publiczne
 * interfejsy modułów"). Stock changes only here, protected by the module
 * boundary rather than an interface.
 */
@Service
class StockService {

	/** v1 has a single warehouse, inserted by the V4 migration. */
	private static final UUID DEFAULT_WAREHOUSE_ID =
			UUID.fromString("11111111-1111-7111-8111-111111111111");

	private final CatalogApi catalog;
	private final StockLevelRepository stockLevels;
	private final StockReceiptRepository receipts;
	private final StockMovementRepository movements;

	StockService(CatalogApi catalog, StockLevelRepository stockLevels,
			StockReceiptRepository receipts, StockMovementRepository movements) {
		this.catalog = catalog;
		this.stockLevels = stockLevels;
		this.receipts = receipts;
		this.movements = movements;
	}

	@Transactional
	StockReceipt receive(UUID productId, long quantity) {
		requireProduct(productId);
		// One atomic upsert: creates the stock level on the first receipt or adds
		// to it, so concurrent receipts always sum (never check-then-insert).
		stockLevels.addOnHand(UuidV7.generate(), productId, DEFAULT_WAREHOUSE_ID, quantity);

		Instant now = Instant.now();
		StockReceipt receipt = receipts.save(
				new StockReceipt(UuidV7.generate(), productId, DEFAULT_WAREHOUSE_ID, quantity, now));
		// Same transaction as the on-hand change, so the history and the stock
		// level never drift apart (user story 26).
		movements.save(StockMovement.receipt(
				UuidV7.generate(), productId, DEFAULT_WAREHOUSE_ID, quantity, now, receipt.getId()));
		return receipt;
	}

	@Transactional(readOnly = true)
	StockLevelResponse stockOf(UUID productId) {
		requireProduct(productId);
		// No stock_level row yet is a storage detail, not an error: 0 / 0 / 0.
		return stockLevels.findByProductIdAndWarehouseId(productId, DEFAULT_WAREHOUSE_ID)
				.map(StockLevelResponse::from)
				.orElse(StockLevelResponse.EMPTY);
	}

	private void requireProduct(UUID productId) {
		if (!catalog.productExists(productId)) {
			throw new UnknownProductException(productId);
		}
	}
}
