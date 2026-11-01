package com.fulfillflow.platform_api.catalog.internal;

import com.fulfillflow.platform_api.catalog.CatalogApi;
import com.fulfillflow.platform_api.catalog.ProductSummary;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The internal implementation of {@link CatalogApi}; other modules see only the
 * interface in the module's root package.
 */
@Service
class CatalogApiImpl implements CatalogApi {

	private final ProductRepository repository;

	CatalogApiImpl(ProductRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean productExists(UUID id) {
		return repository.existsById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductSummary> findProducts(Collection<UUID> ids) {
		return repository.findAllById(ids).stream()
				.map(product -> new ProductSummary(product.getId(), product.getSku(), product.getPrice()))
				.toList();
	}
}
