package com.fulfillflow.platform_api.inventory.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockReceiptRepository extends JpaRepository<StockReceipt, UUID> {
}
