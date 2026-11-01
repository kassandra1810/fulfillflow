# Coding Standards

Sources of truth: `CLAUDE.md`, `docs/PRD.md`, `docs/specs/core-v1.md` (its **[SPEC]** decisions refine the PRD), `docs/adr/`. If an issue contradicts them, follow the docs and flag the conflict.

## Java & Spring Boot

1. Java 25, Spring Boot 4, Maven wrapper (`./mvnw`). Root package `com.fulfillflow.platform_api`.
2. Modular monolith (PRD 5.5): top-level packages `catalog`, `inventory`, `orders`. Each module exposes a small public API (e.g. `CatalogApi`, `InventoryApi`) in its root package, containing only operations that **another module** calls; a method is added in the ticket that adds its first consumer in another module, never ahead of it. The module's own endpoints use services in its internal subpackage. Entities, repositories and implementation live in internal subpackages. Allowed dependencies: `orders → catalog`, `orders → inventory`, `inventory → catalog`; no cycles. Technical code with no domain logic shared by several modules lives in the `shared` module (e.g. `UuidV7`) or `web` (error rendering); both depend on no module. Never copy such code into a module because the original sits in another module's `internal`.
3. Modules exchange only IDs and simple records, never entities. Stock levels change only inside the `inventory` module; other modules change them only through `InventoryApi`.
4. Money is `BigDecimal` with exactly 2 decimal places, never `double`/`float`; reject invalid values with 400, never round silently. Normalize to scale 2 (`setScale(2)`) when the value enters the domain, so every response returns e.g. `19.90`, never `19.9`.
5. All entity IDs are UUIDv7 from `com.fulfillflow.platform_api.shared.UuidV7` (random bits from `SecureRandom`, never `Random`/`ThreadLocalRandom`; never a copy); API resources are addressed by `id`, not `sku`.
6. REST endpoints live under `/api/v1`; errors use Problem Details (RFC 9457) with the status codes from PRD 7. Error rendering lives only in the shared `web` package (`ApiExceptionHandler`); modules throw `ErrorResponseException` subclasses carrying their own status and Problem Detail, and never add their own `@RestControllerAdvice`.
7. Database schema changes only via Flyway migrations (new `V<n>__…` file; never edit an applied one). An invariant backed by a DB constraint (unique index, `CHECK`) is enforced by the constraint: never check-then-insert (`existsBy…` then `save`) — save, flush, and translate the violation into the API error. A constraint violation ends the transaction in PostgreSQL (`current transaction is aborted`): you cannot catch it and keep writing in the same transaction. For "create the row or add to it" use one native `INSERT … ON CONFLICT (…) DO UPDATE` instead of insert-then-recover.
8. Reservation and cancellation follow ADR 0001: conditional `UPDATE` per line, lines sorted by product ID, `READ COMMITTED` isolation (never change it), cancellation via conditional status `UPDATE` — never read-check-`save()` on an entity. `StockMovement` is append-only. `InventoryApi` reserve/release use `@Transactional(propagation = Propagation.MANDATORY)` — they always join the transaction opened by the Orders service (`@Transactional`, default `REQUIRED`) and never open their own (never `REQUIRES_NEW`).

## Naming

9. Code is in English and uses the domain glossary from PRD 4 exactly: `Product`, `Sku`, `version`, `price`, `Warehouse`, `StockLevel`, `onHand`, `reserved`, `available`, `StockReceipt`, `StockMovement` (`RECEIPT`/`RESERVATION`/`RELEASE`), `Order`, `OrderLine`, `Reservation`, `Idempotency-Key`. Do not invent synonyms (e.g. `Item`, `quantity` for `onHand`, `stock` for `StockLevel`).

## Testing

10. Every behaviour change comes with a test; every business rule from PRD 6 must stay covered. Every write path that creates or changes a shared row (stock levels, unique keys, counters) has an `*IT` with real concurrency: N requests released together by a `CountDownLatch` on an `ExecutorService`, then assert all statuses and the final state (see `ProductIT` / `StockIT` `concurrent…` tests).
11. Behaviour (validation, status codes, persistence, business rules) is tested only through the HTTP API `/api/v1` on the full application with real PostgreSQL in Testcontainers — the single behaviour seam from `docs/specs/core-v1.md` (Seamy). These tests are `*IT.java`, extend `AbstractPostgresIntegrationTest` (one singleton container for the whole run — never add `@Testcontainers`/`@Container`) and run in `./mvnw verify` (Failsafe). No H2, no `@WebMvcTest`/`@DataJpaTest` slices with mocked collaborators that duplicate `*IT` cases. Module APIs (`CatalogApi`, `InventoryApi`) have no separate tests: they are covered by the `*IT` tests of the endpoints that use them. A non-HTTP test (e.g. `@ApplicationModuleTest`) only for a guarantee that cannot be observed over HTTP, and only once the spec lists it as a named seam (Seamy).
12. `*Test.java` (Surefire, `./mvnw test`) only for pure logic without Spring or a database (e.g. an ID generator). Do not write them just to have something runnable without Docker.

## Scope

13. Change only what the issue asks for. No unrelated refactors, renames, formatting changes, new dependencies or features — especially nothing listed in PRD 8 (out of scope for v1).
14. Do not edit `docs/PRD.md` or ADRs unless the issue explicitly asks for it. If the issue is ambiguous or conflicts with the docs, comment on the issue instead of guessing.
