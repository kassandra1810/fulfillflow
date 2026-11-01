package com.fulfillflow.platform_api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product")
class Product {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String sku;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal price;

	@Version
	@Column(nullable = false)
	private Long version;

	protected Product() {
	}

	Product(UUID id, String sku, String name, String description, BigDecimal price) {
		this.id = id;
		this.sku = sku;
		this.name = name;
		this.description = description;
		// Always scale 2, like NUMERIC(19,2), so POST and GET return the same price
		// (19.90, not 19.9). Validation already rejects more than 2 decimals, so
		// this never rounds; setScale throws if it ever would.
		this.price = price.setScale(2);
	}

	UUID getId() {
		return id;
	}

	String getSku() {
		return sku;
	}

	String getName() {
		return name;
	}

	String getDescription() {
		return description;
	}

	BigDecimal getPrice() {
		return price;
	}

	Long getVersion() {
		return version;
	}
}
