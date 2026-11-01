package com.fulfillflow.platform_api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The catalog module's public interface, the only way other modules read
 * product data (PRD 5.5, spec "Publiczne interfejsy modułów"). Modules exchange
 * only ids and the simple {@link ProductSummary} record, never catalog entities.
 *
 * <p>Inventory uses it to 404 on receipt and stock reads; Orders uses it to 404
 * for missing products, to copy the unit price and to render {@code sku} in the
 * out-of-stock 409.
 */
public interface CatalogApi {

	/** Whether a product with the given id exists. */
	boolean productExists(UUID id);

	/**
	 * Returns a {@link ProductSummary} for each existing product among the given
	 * ids. Unknown ids are left out, so the result may be smaller than the input;
	 * order is unspecified.
	 */
	List<ProductSummary> findProducts(Collection<UUID> ids);
}
