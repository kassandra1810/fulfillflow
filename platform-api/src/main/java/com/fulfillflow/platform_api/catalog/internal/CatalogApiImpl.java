package com.fulfillflow.platform_api.catalog.internal;

import com.fulfillflow.platform_api.catalog.CatalogApi;
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
}
