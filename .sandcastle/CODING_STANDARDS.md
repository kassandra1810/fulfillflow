# Coding Standards

Sources of truth: `CLAUDE.md`, `docs/PRD.md`, `docs/adr/`. If an issue contradicts them, follow the docs and flag the conflict.

## Java & Spring Boot

1. Java 25, Spring Boot 4, Maven wrapper (`./mvnw`). Root package `com.fulfillflow.platform_api`.
2. Modular monolith (PRD 5.5): top-level packages `catalog`, `inventory`, `orders`. Each module exposes a small public API (e.g. `CatalogApi`, `InventoryApi`) in its root package; entities, repositories and implementation live in internal subpackages. Allowed dependencies: `orders → catalog`, `orders → inventory`, `inventory → catalog`; no cycles.
3. Modules exchange only IDs and simple records, never entities. Stock levels change only through `InventoryApi`.
4. Money is `BigDecimal` with exactly 2 decimal places, never `double`/`float`; reject invalid values with 400, never round silently. Normalize to scale 2 (`setScale(2)`) when the value enters the domain, so every response returns e.g. `19.90`, never `19.9`.
5. All entity IDs are UUIDv7 from the existing `UuidV7` generator (random bits from `SecureRandom`, never `Random`/`ThreadLocalRandom`); API resources are addressed by `id`, not `sku`.
6. REST endpoints live under `/api/v1`; errors use Problem Details (RFC 9457) with the status codes from PRD 7. Error rendering lives only in the shared `web` package (`ApiExceptionHandler`); modules throw `ErrorResponseException` subclasses carrying their own status and Problem Detail, and never add their own `@RestControllerAdvice`.
7. Database schema changes only via Flyway migrations (new `V<n>__…` file; never edit an applied one). An invariant backed by a DB constraint (unique index, `CHECK`) is enforced by the constraint: never check-then-insert (`existsBy…` then `save`) — save, flush, and translate the violation into the API error.
8. Reservation and cancellation follow ADR 0001: conditional `UPDATE` per line, lines sorted by product ID, `READ COMMITTED` isolation (never change it), cancellation via conditional status `UPDATE` — never read-check-`save()` on an entity. `StockMovement` is append-only.

## Naming

9. Code is in English and uses the domain glossary from PRD 4 exactly: `Product`, `Sku`, `version`, `price`, `Warehouse`, `StockLevel`, `onHand`, `reserved`, `available`, `StockReceipt`, `StockMovement` (`RECEIPT`/`RESERVATION`/`RELEASE`), `Order`, `OrderLine`, `Reservation`, `Idempotency-Key`. Do not invent synonyms (e.g. `Item`, `quantity` for `onHand`, `stock` for `StockLevel`).

## Testing

10. Every behaviour change comes with a test; every business rule from PRD 6 must stay covered.
11. Behaviour (validation, status codes, persistence, business rules) is tested only through the HTTP API `/api/v1` on the full application with real PostgreSQL in Testcontainers — the single behaviour seam from `docs/specs/core-v1.md` (Seamy). These tests are `*IT.java`, extend `AbstractPostgresIntegrationTest` (one singleton container for the whole run — never add `@Testcontainers`/`@Container`) and run in `./mvnw verify` (Failsafe). No H2, no `@WebMvcTest`/`@DataJpaTest` slices with mocked collaborators that duplicate `*IT` cases.
12. `*Test.java` (Surefire, `./mvnw test`) only for pure logic without Spring or a database (e.g. an ID generator). Do not write them just to have something runnable without Docker.

## Scope

13. Change only what the issue asks for. No unrelated refactors, renames, formatting changes, new dependencies or features — especially nothing listed in PRD 8 (out of scope for v1).
14. Do not edit `docs/PRD.md` or ADRs unless the issue explicitly asks for it. If the issue is ambiguous or conflicts with the docs, comment on the issue instead of guessing.
