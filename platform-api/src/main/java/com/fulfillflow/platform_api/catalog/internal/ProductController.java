package com.fulfillflow.platform_api.catalog.internal;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

	@GetMapping("/{id}")
	ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		Product product = service.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		return ResponseEntity.ok(ProductResponse.from(product));
	}
}
