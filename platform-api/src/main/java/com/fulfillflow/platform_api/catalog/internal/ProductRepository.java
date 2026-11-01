package com.fulfillflow.platform_api.catalog.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, UUID> {

	boolean existsBySku(String sku);
}
