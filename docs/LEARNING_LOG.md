# Learning log

Mój dziennik: co zadziałało, co nie, czego się nauczyłam.

## 2026-10-28 — Lekcja 4: `/to-spec` i `/to-tickets`

### Wstępna lista (przed `/to-tickets`)

Szkic z `PLAN_FULFILLFLOW.md`, napisany przed sesją `/grill-with-docs`:

- **FF-002:** `POST /api/v1/products` — PostgreSQL, Flyway, test z Testcontainers
- **FF-003:** `GET /api/v1/products` — paginacja
- **FF-004:** przyjęcie towaru i odczyt stanu
- **FF-005:** utworzenie zamówienia z rezerwacją (wszystko albo nic)
- **FF-006:** test równoczesnych rezerwacji ostatnich sztuk
- **FF-007:** anulowanie zamówienia (idempotentne)

### Zestawienie z ticketami T01–T13

| Wstępna lista | Tickety | Różnica |
|---|---|---|
| FF-002 | T01, T02 | Rozbite: pełna ścieżka od endpointu do bazy osobno, walidacja i Problem Details osobno |
| FF-003 | T04 | Doprecyzowane: limity `page`/`size`, 400, stała kolejność, filtr `?sku=` |
| FF-004 | T06 | Doszło: `CatalogApi`/`InventoryApi`, wpis `RECEIPT`, stan `0 / 0 / 0` |
| FF-005 | T08, T09 | Rozbite: zamówienie jednopozycyjne, potem wielopozycyjne (sortowanie, wszystkie braki w 409) |
| FF-006 | T10 | Doszło: brak deadlocków, przypięcie `READ COMMITTED`, pula HikariCP |
| FF-007 | T11 | Doszło: warunkowy `UPDATE` na statusie, wpisy `RELEASE`, test reguły 6 |
| — | T03 | Nowe: granice modułów (Spring Modulith) i OpenAPI |
| — | T05 | Nowe: edycja produktu (`PATCH` z `version`) |
| — | T07 | Nowe: historia ruchów magazynowych |
| — | T12, T13 | Nowe: `Idempotency-Key` |

Uwaga: część nowych ticketów (T05, T07, T12–T13) wynika z decyzji podjętych w `/grill-with-docs` już po napisaniu wstępnej listy, a nie tylko z pracy `/to-tickets`.

### Moje wnioski

- Co agent zrobił lepiej:
- Czego brakowało / co poprawiłam:
- Co zmienię w workflow:
