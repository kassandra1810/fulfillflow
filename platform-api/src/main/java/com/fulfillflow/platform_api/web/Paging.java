package com.fulfillflow.platform_api.web;

/**
 * The page/size limits shared by every paginated endpoint ({@code GET /products},
 * {@code GET /products/{id}/stock-movements}; spec "Paginacja"). Declared once
 * here so the two controllers reuse the same {@code @Min}/{@code @Max} bounds
 * and default size instead of each repeating the numbers. Lives in the shared
 * {@code web} package, which depends on no module.
 */
public final class Paging {

	/** Default page size when the client sends no {@code size}. */
	public static final int DEFAULT_SIZE = 20;

	/** Largest page size a client may ask for. */
	public static final int MAX_SIZE = 100;

	/** Highest page whose row offset (page * size) still fits the int the database query takes. */
	public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

	private Paging() {
	}
}
