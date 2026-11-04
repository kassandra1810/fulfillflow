package com.fulfillflow.platform_api.catalog.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

	private static final int MAX_PAGE_SIZE = 100;

	/** Highest page whose row offset (page * size) still fits the int the database query takes. */
	private static final int MAX_PAGE = Integer.MAX_VALUE / MAX_PAGE_SIZE;

	private final ProductService service;

	ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
		Product product = service.create(request.sku(), request.name(), request.description(), request.price());
		ProductResponse body = ProductResponse.from(product);
		return ResponseEntity.created(URI.create("/api/v1/products/" + product.getId())).body(body);
	}

	/**
	 * Lists products, one page at a time, always ordered by sku ascending (the
	 * client never chooses the sort). {@code page} starts at 0, {@code size}
	 * defaults to 20 and may not exceed 100; out-of-range values (including a
	 * page whose row offset would overflow the database query) are rejected
	 * with 400 rather than silently clamped. An optional {@code sku} (surrounding
	 * whitespace stripped, as on create) narrows the page to the single matching
	 * product, or none — never a 404. The body is a stable {@link PagedModel}
	 * DTO, not a serialized {@code Page}.
	 */
	@GetMapping
	PagedModel<ProductResponse> list(
			@RequestParam(required = false) String sku,
			@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return new PagedModel<>(service.list(sku, page, size).map(ProductResponse::from));
	}

	@GetMapping("/{id}")
	ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		Product product = service.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		return ResponseEntity.ok(ProductResponse.from(product));
	}
}
