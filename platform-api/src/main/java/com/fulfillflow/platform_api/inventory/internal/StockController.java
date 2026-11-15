package com.fulfillflow.platform_api.inventory.internal;

import com.fulfillflow.platform_api.web.Paging;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory HTTP endpoints: receiving stock and reading a product's stock. Both
 * use the module's internal {@link StockService}; existence of the product is
 * checked through the catalog's public API, yielding 404 for an unknown one.
 */
@RestController
@RequestMapping("/api/v1")
class StockController {

	private final StockService service;

	StockController(StockService service) {
		this.service = service;
	}

	@PostMapping("/stock-receipts")
	ResponseEntity<StockReceiptResponse> receive(@Valid @RequestBody StockReceiptRequest request) {
		StockReceipt receipt = service.receive(request.productId(), request.quantity());
		return ResponseEntity.status(HttpStatus.CREATED).body(StockReceiptResponse.from(receipt));
	}

	@GetMapping("/products/{id}/stock")
	ResponseEntity<StockLevelResponse> stock(@PathVariable UUID id) {
		return ResponseEntity.ok(service.stockOf(id));
	}

	/**
	 * One page of a product's append-only movement history, newest first (the
	 * client never chooses the sort; the service fixes it). Paging follows the
	 * same rules and limits as the product list ({@link Paging}); bad {@code page}
	 * or {@code size} is a 400 with {@code errors.page}/{@code errors.size},
	 * rendered centrally. An unknown product is a 404; one with no movements is a
	 * 200 with an empty page. The body is a stable {@link PagedModel} DTO.
	 */
	@GetMapping("/products/{id}/stock-movements")
	PagedModel<StockMovementResponse> movements(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) @Max(Paging.MAX_PAGE) int page,
			@RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_SIZE) int size) {
		return new PagedModel<>(service.movementsOf(id, page, size).map(StockMovementResponse::from));
	}
}
