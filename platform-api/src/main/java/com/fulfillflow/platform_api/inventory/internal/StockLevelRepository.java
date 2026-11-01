package com.fulfillflow.platform_api.inventory.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

	Optional<StockLevel> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

	/**
	 * Adds to {@code on_hand} in one atomic statement: creates the stock level on
	 * the first receipt, otherwise adds to the existing row. Concurrent receipts
	 * — including concurrent first receipts of a new product — all sum, with no
	 * read-modify-save and no constraint violation to recover from (in
	 * PostgreSQL a failed INSERT aborts the whole transaction).
	 *
	 * @param newId the id used only if this call creates the row
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO stock_level (id, product_id, warehouse_id, on_hand, reserved)
			VALUES (:newId, :productId, :warehouseId, :quantity, 0)
			ON CONFLICT (product_id, warehouse_id)
			DO UPDATE SET on_hand = stock_level.on_hand + EXCLUDED.on_hand
			""")
	void addOnHand(@Param("newId") UUID newId,
			@Param("productId") UUID productId,
			@Param("warehouseId") UUID warehouseId,
			@Param("quantity") long quantity);
}
