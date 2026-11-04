package com.fulfillflow.platform_api.catalog.internal;

import com.fulfillflow.platform_api.shared.UuidV7;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {

	/** Unique index on product.sku (V1 migration). */
	private static final String SKU_UNIQUE_INDEX = "ux_product_sku";

	private final ProductRepository repository;
	private final Validator validator;

	ProductService(ProductRepository repository, Validator validator) {
		this.repository = repository;
		this.validator = validator;
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

	/**
	 * Applies a PATCH to a product under optimistic locking. Order of failures
	 * (spec): validation 400 → product missing 404 → version mismatch 409.
	 *
	 * <p>The request {@code version} is compared explicitly to the stored version:
	 * JPA {@code @Version} alone cannot 409, because Hibernate compares the loaded
	 * version with itself and ignores the request. Two concurrent edits with the
	 * same version both pass that check; the second is then stopped by
	 * {@code @Version} at flush ({@link OptimisticLockingFailureException}), which
	 * is also a 409, not a 500. The stored {@code @Version} is never set by hand.
	 *
	 * <p>A PATCH that changes nothing leaves the entity clean, so Hibernate issues
	 * no UPDATE and the version stays put — no change is lost, so no forced bump.
	 */
	@Transactional
	Product update(UUID id, UpdateProductRequest request) {
		request.validate(validator);

		Product product = repository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));

		if (!product.getVersion().equals(request.version())) {
			throw new VersionConflictException();
		}

		request.applyTo(product);
		try {
			return repository.saveAndFlush(product);
		} catch (OptimisticLockingFailureException e) {
			throw new VersionConflictException();
		}
	}

	/**
	 * One page of products, always ordered by sku ascending. With a {@code sku}
	 * (surrounding whitespace stripped, as on create) the page holds the single
	 * matching product or none; the client never gets a 404 for a missing sku.
	 */
	@Transactional(readOnly = true)
	Page<Product> list(String sku, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sku"));
		return sku == null
				? repository.findAll(pageable)
				: repository.findBySku(sku.strip(), pageable);
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
