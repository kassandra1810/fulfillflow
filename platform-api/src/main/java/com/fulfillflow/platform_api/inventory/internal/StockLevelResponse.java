package com.fulfillflow.platform_api.inventory.internal;

/**
 * Stock of a product as seen over the API: {@code available = onHand - reserved}.
 * A product that has never been received reads as all zero.
 */
record StockLevelResponse(long onHand, long reserved, long available) {

	static final StockLevelResponse EMPTY = new StockLevelResponse(0, 0, 0);

	static StockLevelResponse from(StockLevel level) {
		return new StockLevelResponse(level.getOnHand(), level.getReserved(), level.getAvailable());
	}
}
