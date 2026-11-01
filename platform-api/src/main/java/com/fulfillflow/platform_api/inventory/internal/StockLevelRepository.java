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
	 * Adds to {@code on_hand} atomically in the database, so concurrent receipts
	 * of the same product sum instead of losing an update (no read-modify-save).
	 *
	 * @return the number of rows changed: 1 if the stock level exists, 0 if the
	 *         product has never been received (no row yet).
	 */
	@Modifying
	@Query("update StockLevel s set s.onHand = s.onHand + :quantity "
			+ "where s.productId = :productId and s.warehouseId = :warehouseId")
	int addOnHand(@Param("productId") UUID productId,
			@Param("warehouseId") UUID warehouseId,
			@Param("quantity") long quantity);
}
