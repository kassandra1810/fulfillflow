# FulfillFlow — plan roboczy

> Zasada: budujemy małe, działające fragmenty, prowadzone workflowem Matta Pococka (AI Hero, „AI Skills for Real Engineers”). Plan aktualizujemy tylko wtedy, gdy jest to potrzebne do kolejnego kroku. Jeśli lekcja kursu mówi inaczej niż ten plan, **wygrywa lekcja**, a plan poprawiamy.

## Cel

1. Nauczyć się powtarzalnego workflowu pracy z agentem AI (do użycia w pracy).
2. Zbudować FulfillFlow Core v1 jako projekt portfolio: produkty, stany magazynowe, zamówienia bez oversellingu.

## Stos v1

- Java 25 LTS, Spring Boot 4.x, Maven
- PostgreSQL, Spring Data JPA / Hibernate, Flyway
- REST API, JUnit + Testcontainers, Docker Compose, GitHub Actions
- Moduły: Catalog → Inventory → Orders

**Nie teraz:** Kafka, mikroserwisy, Keycloak, AWS, Terraform, Grafana, frontend (patrz: Parking lot).

## Pliki w repozytorium

| Plik | Po co |
|---|---|
| `docs/PRD.md` | Wymagania v1 — wejście do `/grill-with-docs` |
| `docs/specs/` | Specyfikacje z `/to-spec` |
| `docs/adr/` | Krótkie zapisy ważnych decyzji (np. mechanizm blokady) |
| `CLAUDE.md` / `AGENTS.md` | Stałe instrukcje dla agenta: stos, komendy build/test, zasady projektu |
| `docs/LEARNING_LOG.md` | Mój dziennik: co zadziałało, co nie, czego się nauczyłam |

---

## Ścieżka: kurs + aplikacja

Tempo: jedna lekcja na 1–3 dni. Odpowiadam na pytanie z lekcji dopiero, gdy zrobię ćwiczenie na FulfillFlow.

### Lekcja 1 — instalacja i fundament
**Kurs:** instalacja skilli, wybór ścieżki (*My own workflows*).
**Aplikacja — FF-001: szkielet usługi**
- Projekt `platform-api` ze https://start.spring.io/ (Maven, Java 25, Spring Boot 4.x; Spring Web, Actuator, Validation).
- Test kontekstu Spring, README z instrukcją uruchomienia.
- GitHub Actions: `./mvnw verify` na każdy push.
- `CLAUDE.md` / `AGENTS.md`: stos, komendy (`./mvnw verify`), zasada „po każdej zmianie uruchom testy”.
- Skille zainstalowane w tym repo.

**Gotowe, gdy:** `./mvnw test` przechodzi, `GET /actuator/health` zwraca `UP`, CI zielone, pierwszy commit `chore: bootstrap Spring Boot application`.

### Lekcja 2 — `/grill-with-docs`: wymagania bez mgły
**Aplikacja:** sesja grillowania na `docs/PRD.md`.
- Przechodzę przez otwarte pytania z PRD i założenia **[ZAŁOŻENIE]**.
- Decyzje podejmuję ja; agent pyta, kwestionuje i proponuje alternatywy.
- Wynik: zaktualizowany PRD (decyzje zamiast pytań), uzupełniony słownik domeny, lista pytań, na które nie znam odpowiedzi bez eksperymentu.

**Gotowe, gdy:** w PRD nie ma niejasności poza tymi, które świadomie zostawiam do prototypu.

### Lekcja 3 — `/prototype` i `/handoff`: sprawdź niepewne
**Aplikacja:** prototyp najbardziej ryzykownej części — **rezerwacja ostatnich sztuk przy równoczesnych zamówieniach**.
- Osobny branch `prototype/stock-locking`, kod do wyrzucenia.
- Porównanie: optimistic locking vs `SELECT … FOR UPDATE` vs atomowy `UPDATE … WHERE`, test 100 równoczesnych żądań na 5 sztuk (Testcontainers + PostgreSQL).
- Decyzja zapisana jako `docs/adr/0001-stock-reservation-locking.md`.
- `/handoff` na koniec sesji, żeby następna sesja agenta startowała z pełnym kontekstem.

**Gotowe, gdy:** wiem, który mechanizm wybieram, umiem wyjaśnić dlaczego i w jakich warunkach zawiedzie. Branch prototypu nie trafia do `main`.

### Lekcja 4 — `/to-spec` i `/to-tickets`: duża praca w małych kawałkach
**Aplikacja:**
- `/to-spec` z PRD + ADR → specyfikacja v1 w `docs/specs/`.
- `/to-tickets` → backlog vertical slices (zastępuje wstępną listę FF-002…FF-006 poniżej).
- Porównuję wygenerowane tickety ze swoją wstępną listą: co agent zrobił lepiej, czego brakuje.

**Gotowe, gdy:** każdy ticket ma kryteria akceptacji, jest mały (≈ jedna sesja) i da się go przetestować niezależnie.

### Lekcja 5 — Sandcastle: agent pracuje sam, bezpiecznie
**Aplikacja:** pierwszy ticket z backlogu (np. tworzenie produktu) realizowany przez agenta AFK w Sandcastle.
- Agent pracuje na osobnym branchu, ja w tym czasie nie ingeruję.
- Agent musi uruchamiać `./mvnw verify` jako pętlę zwrotną.

**Gotowe, gdy:** jest branch z implementacją i testami, gotowy do review.

### Lekcja 6 — `/code-review`: każda iteracja uczy następną
**Aplikacja:** review brancha z lekcji 5.
- `/code-review` + mój własny przegląd zmienionych plików.
- Każdy powtarzający się błąd agenta → reguła w `CLAUDE.md` / `AGENTS.md` lub poprawka skilla.
- Merge dopiero po moim zrozumieniu każdej zmiany.

**Gotowe, gdy:** ticket zmergowany, CI zielone, instrukcje dla agenta zaktualizowane.

### Lekcja 7 — pełna pętla
**Aplikacja:** kolejny ficzer przechodzi cały cykl od grillowania do review — najlepiej **zamówienia z rezerwacją** (z mechanizmem z ADR 0001).

**Gotowe, gdy:** ficzer działa, a ja mam spisany swój workflow w `docs/LEARNING_LOG.md`.

---

## Po kursie: pętla do końca v1

Dla każdego ficzera:

1. `/grill-with-docs` — doprecyzowanie (krótko, jeśli PRD już to pokrywa)
2. `/prototype` — tylko gdy jest niepewna część
3. `/to-spec` → `/to-tickets`
4. Implementacja ticketu po tickecie (sesja z agentem lub AFK w Sandcastle)
5. `/code-review` + mój review → merge
6. Wpis w `LEARNING_LOG.md`, poprawki w instrukcjach agenta

Zasady stałe:
- Jeden aktywny ticket naraz; agent nie buduje niczego „na zapas”.
- Agent zawsze uruchamia testy; ja zawsze uruchamiam aplikację i sprawdzam działanie.
- Nie merguję kodu, którego nie umiem wyjaśnić.

## Wstępny backlog (szkic — zastąpi go `/to-tickets` w lekcji 4)

- **FF-002:** `POST /api/v1/products` — PostgreSQL, Flyway, test z Testcontainers
- **FF-003:** `GET /api/v1/products` — paginacja
- **FF-004:** przyjęcie towaru i odczyt stanu
- **FF-005:** utworzenie zamówienia z rezerwacją (wszystko albo nic)
- **FF-006:** test równoczesnych rezerwacji ostatnich sztuk
- **FF-007:** anulowanie zamówienia (idempotentne)

## Definicja końca v1

REST API działa dla produktów, stanów magazynu i zamówień; równoczesne rezerwacje nie dopuszczają oversellingu; migracje i testy integracyjne działają; wszystko da się uruchomić z README; CI jest zielone; kluczowe decyzje są w ADR.

## Parking lot — po v1

Kafka + Outbox + idempotencja, osobne serwisy powiadomień i raportowania, OAuth2/OIDC i Keycloak, Prometheus + Grafana, Terraform + AWS, testy wydajnościowe. Każdą nową technologię najpierw poznaję sama na małym przykładzie, dopiero potem buduję z agentem.
