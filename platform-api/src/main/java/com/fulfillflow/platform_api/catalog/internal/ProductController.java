package com.fulfillflow.platform_api.catalog.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
	 * defaults to 20 and may not exceed 100; out-of-range values are rejected
	 * with 400 rather than silently clamped. An optional {@code sku} narrows the
	 * page to the single matching product, or none — never a 404. The body is a
	 * stable {@link PagedModel} DTO, not a serialized {@code Page}.
	 */
	@GetMapping
	PagedModel<ProductResponse> list(
			@RequestParam(required = false) String sku,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sku"));
		return new PagedModel<>(service.list(sku, pageable).map(ProductResponse::from));
	}

	@GetMapping("/{id}")
	ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		Product product = service.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		return ResponseEntity.ok(ProductResponse.from(product));
	}
}
