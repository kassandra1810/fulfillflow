package com.fulfillflow.platform_api.catalog;

import java.util.UUID;

/**
 * The catalog module's public interface, the only way other modules read
 * product data (PRD 5.5, spec "Publiczne interfejsy modułów"). Modules exchange
 * only ids and simple records, never catalog entities.
 *
 * <p>A method is added here only together with its first consumer in another
 * module, whose endpoint's {@code *IT} test covers it (spec "Seamy"). Inventory
 * uses {@link #productExists} to 404 on receipt and stock reads (T06); Orders
 * will add a lookup of {@code sku} and price when it needs one (T08).
 */
public interface CatalogApi {

	/** Whether a product with the given id exists. */
	boolean productExists(UUID id);
}
