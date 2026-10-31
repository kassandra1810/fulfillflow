# Coding Standards

Sources of truth: `CLAUDE.md`, `docs/PRD.md`, `docs/adr/`. If an issue contradicts them, follow the docs and flag the conflict.

## Java & Spring Boot

1. Java 25, Spring Boot 4, Maven wrapper (`./mvnw`). Root package `com.fulfillflow.platform_api`.
2. Modular monolith (PRD 5.5): top-level packages `catalog`, `inventory`, `orders`. Each module exposes a small public API (e.g. `CatalogApi`, `InventoryApi`) in its root package; entities, repositories and implementation live in internal subpackages. Allowed dependencies: `orders → catalog`, `orders → inventory`, `inventory → catalog`; no cycles.
3. Modules exchange only IDs and simple records, never entities. Stock levels change only through `InventoryApi`.
4. Money is `BigDecimal` with exactly 2 decimal places, never `double`/`float`; reject invalid values with 400, never round silently.
5. All entity IDs are UUIDv7; API resources are addressed by `id`, not `sku`.
6. REST endpoints live under `/api/v1`; errors use Problem Details (RFC 9457) with the status codes from PRD 7.
7. Database schema changes only via Flyway migrations.
8. Reservation and cancellation follow ADR 0001: conditional `UPDATE` per line, lines sorted by product ID, `READ COMMITTED` isolation (never change it), cancellation via conditional status `UPDATE` — never read-check-`save()` on an entity. `StockMovement` is append-only.

## Naming

9. Code is in English and uses the domain glossary from PRD 4 exactly: `Product`, `Sku`, `version`, `price`, `Warehouse`, `StockLevel`, `onHand`, `reserved`, `available`, `StockReceipt`, `StockMovement` (`RECEIPT`/`RESERVATION`/`RELEASE`), `Order`, `OrderLine`, `Reservation`, `Idempotency-Key`. Do not invent synonyms (e.g. `Item`, `quantity` for `onHand`, `stock` for `StockLevel`).

## Testing

10. Every behaviour change comes with a test; every business rule from PRD 6 must stay covered.
11. Unit tests: `*Test.java`, no Docker or database, run by `./mvnw test` (Surefire).
12. Tests that need PostgreSQL/Testcontainers: `*IT.java`, real PostgreSQL (no H2), run only in `./mvnw verify` (Failsafe).

## Scope

13. Change only what the issue asks for. No unrelated refactors, renames, formatting changes, new dependencies or features — especially nothing listed in PRD 8 (out of scope for v1).
14. Do not edit `docs/PRD.md` or ADRs unless the issue explicitly asks for it. If the issue is ambiguous or conflicts with the docs, comment on the issue instead of guessing.
