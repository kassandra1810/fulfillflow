package com.fulfillflow.platform_api.catalog.internal;

import com.fulfillflow.platform_api.web.Paging;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

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
			@RequestParam(defaultValue = "0") @Min(0) @Max(Paging.MAX_PAGE) int page,
			@RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_SIZE) int size) {
		return new PagedModel<>(service.list(sku, page, size).map(ProductResponse::from));
	}

	@GetMapping("/{id}")
	ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		Product product = service.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		return ResponseEntity.ok(ProductResponse.from(product));
	}

	/**
	 * Partially updates a product under optimistic locking. Only the fields sent
	 * change; {@code "description": null} clears the description, an omitted field
	 * is left alone. {@code version} is required; a stale one is a 409. Validation
	 * is not {@code @Valid} here because PATCH needs to tell an absent field from an
	 * explicit {@code null} — {@link UpdateProductRequest} carries both and
	 * validates itself in the service.
	 */
	@PatchMapping("/{id}")
	ResponseEntity<ProductResponse> update(@PathVariable UUID id, @RequestBody UpdateProductRequest request) {
		Product product = service.update(id, request);
		return ResponseEntity.ok(ProductResponse.from(product));
	}
}
