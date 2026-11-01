package com.fulfillflow.platform_api.catalog.internal;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {

	private final ProductRepository repository;

	ProductService(ProductRepository repository) {
		this.repository = repository;
	}

	@Transactional
	Product create(String sku, String name, String description, BigDecimal price) {
		if (repository.existsBySku(sku)) {
			throw new DuplicateSkuException(sku);
		}
		Product product = new Product(UuidV7.generate(), sku, name, description, price);
		return repository.save(product);
	}

	@Transactional(readOnly = true)
	Optional<Product> findById(UUID id) {
		return repository.findById(id);
	}
}
