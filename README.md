# FulfillFlow

Backend platformy FulfillFlow — aplikacja Spring Boot 4 na Javie 25.

## Wymagania
- JDK 25
- Docker (PostgreSQL przez Docker Compose; testy integracyjne używają Testcontainers)
- Maven nie jest wymagany — projekt używa wrappera `./mvnw`

## Baza danych
Aplikacja używa PostgreSQL, a schemat jest zarządzany wyłącznie migracjami Flyway.
PostgreSQL do lokalnego uruchomienia podnosi Docker Compose (z katalogu głównego repo):
```bash
docker compose up -d
```
Compose udostępnia bazę `fulfillflow` na `localhost:5432` (użytkownik i hasło: `fulfillflow`).
Te same wartości są domyślne w `application.properties`.

## Uruchomienie
```bash
docker compose up -d        # z katalogu głównego repo
cd platform-api
./mvnw spring-boot:run
```
Aplikacja startuje domyślnie na `http://localhost:8080`. Flyway tworzy schemat przy starcie.

### Przykład
```bash
curl -s -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-001","name":"Widget","description":"A nice widget","price":19.90}'
# 201 Created + {"id":"...","sku":"SKU-001",...,"version":0}

curl -s http://localhost:8080/api/v1/products/<id>
# 200 OK + ten sam produkt
```

## Dokumentacja API (OpenAPI)
Aplikacja udostępnia dokumentację OpenAPI wszystkich endpointów (generowaną przez springdoc):
- dokument OpenAPI (JSON): `http://localhost:8080/v3/api-docs`
- Swagger UI (przeglądarka): `http://localhost:8080/swagger-ui.html`

## Architektura (moduły)
Modularny monolit pilnowany przez Spring Modulith: moduły najwyższego poziomu (`catalog`, …)
wystawiają mały publiczny interfejs (np. `CatalogApi`), a implementacja żyje w podpakietach
wewnętrznych. Złamanie granicy modułu lub cykl zależności przerywa build — pilnuje tego test
`ApplicationModulesTest` (`ApplicationModules.of(...).verify()`) uruchamiany przez `./mvnw test`
i `./mvnw verify`.

## Testy
Testy integracyjne (`*IT`) uruchamiają prawdziwy PostgreSQL w Testcontainers — wymagają działającego Dockera, ale nie potrzebują `docker compose up`.
```bash
cd platform-api
./mvnw verify
```
`./mvnw test` uruchamia tylko testy jednostkowe (Surefire); testy integracyjne (`*IT`) uruchamia `./mvnw verify` (Failsafe).
