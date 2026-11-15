package com.fulfillflow.platform_api.inventory.internal;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

	/** One page of a product's movements; the order comes from the {@link Pageable}. */
	Page<StockMovement> findByProductId(UUID productId, Pageable pageable);
}
