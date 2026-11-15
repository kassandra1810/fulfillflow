package com.fulfillflow.platform_api.catalog.internal;

/** Normalization shared by the product text fields that are stored verbatim after trimming. */
final class ProductText {

	private ProductText() {
	}

	/**
	 * Strips surrounding whitespace, preserving {@code null}. Applied to {@code sku}
	 * and {@code name} in both POST and PATCH so the stored value never carries
	 * leading/trailing whitespace and {@code @Size}/{@code @NotBlank} are checked
	 * against the trimmed value (spec: core-v1). {@code description} is free text and
	 * is not trimmed.
	 */
	static String strip(String value) {
		return value == null ? null : value.strip();
	}
}
