package com.fulfillflow.platform_api.inventory.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
