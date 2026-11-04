package com.fulfillflow.platform_api.catalog.internal;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findBySku(String sku, Pageable pageable);
}
