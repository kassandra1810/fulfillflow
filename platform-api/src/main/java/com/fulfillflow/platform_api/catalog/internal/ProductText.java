package com.fulfillflow.platform_api.catalog.internal;

/**
 * The one trimming rule for product text that is stored without surrounding
 * blanks ({@code sku}, {@code name}; spec core-v1). Request DTOs apply it as the
 * value enters, so {@code @NotBlank}/{@code @Size} see the trimmed value.
 * {@code description} is free text and never goes through here.
 *
 * <p>"Blank" is wider than {@link String#strip()}: besides Java whitespace it
 * covers Unicode space separators (no-break space U+00A0, U+2007, U+202F) and
 * zero-width characters (U+200B–U+200D, U+2060, BOM U+FEFF). Otherwise a value
 * made only of such invisible characters would pass {@code @NotBlank} and be
 * stored as an empty-looking name or sku.
 */
final class ProductText {

	private ProductText() {
	}

	/** {@code value} without leading/trailing blanks; {@code null} stays {@code null}. */
	static String strip(String value) {
		if (value == null) {
			return null;
		}
		int start = 0;
		int end = value.length();
		while (start < end && isBlank(value.charAt(start))) {
			start++;
		}
		while (end > start && isBlank(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	private static boolean isBlank(char c) {
		return Character.isWhitespace(c)
				|| Character.isSpaceChar(c)
				|| (c >= '​' && c <= '‍')
				|| c == '⁠'
				|| c == '﻿';
	}
}
