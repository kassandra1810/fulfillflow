package com.fulfillflow.platform_api.catalog.internal;

/** Normalization shared by the product text fields that are stored verbatim after trimming. */
final class ProductText {

	private ProductText() {
	}

	/**
	 * Strips surrounding whitespace, preserving {@code null}, so a stored value never
	 * carries leading/trailing whitespace and {@code @Size}/{@code @NotBlank} are
	 * checked against the trimmed value (spec: core-v1). Applied to {@code sku} (POST;
	 * immutable in PATCH) and {@code name} (POST and PATCH). {@code description} is
	 * free text and is not trimmed.
	 */
	static String strip(String value) {
		return value == null ? null : value.strip();
	}
}
