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
		this.price = scaled(price);
	}

	/**
	 * Changes the name. State changes go through this method, not a public setter,
	 * so the entity stays in control of its own state.
	 */
	void changeName(String name) {
		this.name = name;
	}

	/** Changes the description; a {@code null} clears it (PATCH "description": null). */
	void changeDescription(String description) {
		this.description = description;
	}

	/** Changes the price, normalized to scale 2 like the constructor. */
	void changePrice(BigDecimal price) {
		this.price = scaled(price);
	}

	// Always scale 2, like NUMERIC(19,2), so POST, PATCH and GET return the same
	// price (19.90, not 19.9). Validation already rejects more than 2 decimals, so
	// this never rounds; setScale throws if it ever would.
	private static BigDecimal scaled(BigDecimal price) {
		return price.setScale(2);
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
