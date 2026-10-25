# FulfillFlow

## Stos
- Java 25
- Spring Boot 4 (moduły: webmvc, validation, actuator)
- Maven (przez wrapper `./mvnw`)

## Struktura
- `platform-api/` — aplikacja Spring Boot (pakiet `com.fulfillflow.platform_api`)
- `docs/` — PRD i plan projektu

## Testy
```bash
cd platform-api && ./mvnw verify
```

## Zasady
- Po każdej zmianie uruchom `./mvnw verify` w katalogu `platform-api` i upewnij się, że przechodzi, zanim uznasz zadanie za skończone.
- CI (`.github/workflows/ci.yml`) uruchamia `./mvnw -B verify` na każdy push i pull request.
