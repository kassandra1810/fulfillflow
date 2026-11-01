package com.fulfillflow.platform_api.inventory.internal;

import java.util.UUID;

record StockReceiptResponse(UUID id, UUID productId, long quantity) {

	static StockReceiptResponse from(StockReceipt receipt) {
		return new StockReceiptResponse(receipt.getId(), receipt.getProductId(), receipt.getQuantity());
	}
}
