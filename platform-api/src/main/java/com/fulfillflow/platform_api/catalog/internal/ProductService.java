package com.fulfillflow.platform_api.catalog.internal;

import com.fulfillflow.platform_api.shared.UuidV7;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {

	/** Unique index on product.sku (V1 migration). */
	private static final String SKU_UNIQUE_INDEX = "ux_product_sku";

	private final ProductRepository repository;

	ProductService(ProductRepository repository) {
		this.repository = repository;
	}

	@Transactional
	Product create(String sku, String name, String description, BigDecimal price) {
		Product product = new Product(UuidV7.generate(), sku, name, description, price);
		// The unique index is the source of truth for sku uniqueness: checking
		// first would let two concurrent creates both pass and the loser get a 500.
		try {
			return repository.saveAndFlush(product);
		} catch (DataIntegrityViolationException e) {
			if (violates(e, SKU_UNIQUE_INDEX)) {
				throw new DuplicateSkuException(sku);
			}
			throw e;
		}
	}

	@Transactional(readOnly = true)
	Optional<Product> findById(UUID id) {
		return repository.findById(id);
	}

	private static boolean violates(Throwable e, String constraint) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& constraint.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}
}
