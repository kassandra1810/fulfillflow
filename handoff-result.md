# Handoff: wynik prototypu rezerwacji, do wątku planowania

Pytanie i zakres prototypu: `handoff-prototype.md`. Mechanizm, który był sprawdzany: `docs/PRD.md` 6.1 i `docs/adr/0001-reservation-conditional-update.md`.

## Werdykt

**Tak, mechanizm z PRD 6.1 działa**, pod dwoma warunkami, których PRD i ADR dziś nie zapisują:

1. Transakcje rezerwacji i anulowania muszą działać w **`READ COMMITTED`**.
2. Anulowanie musi zmieniać status **warunkowym `UPDATE … WHERE status = 'RESERVED'`**. Towar zwalniamy w tej samej transakcji i tylko wtedy, gdy ten `UPDATE` zmienił 1 wiersz.

Bez warunku 2 prototyp pokazał prawdziwy overbooking. Bez warunku 1 sklep odmawia klientom, choć ma towar.

## Gdzie są dowody

- Branch `prototype/stock-locking`, commity `d6de108` i `aba3cdb`. Liczby z każdego scenariusza są w treści commitów.
- Test: `platform-api/src/test/java/com/fulfillflow/platform_api/prototype/reservation/ReservationPrototypeTest.java`. Czyste JDBC, PostgreSQL 17 w Testcontainers, każdy wątek z własnym połączeniem. Każdy scenariusz współbieżny powtórzony 20 razy.
- Uruchomienie: `cd platform-api && ./mvnw test -Dtest=ReservationPrototypeTest`. Raporty run po runie wypisuje na stdout, całość trwa ok. 100 s.

## Co wykazały testy (skrót)

| Reguła / pytanie | Wynik |
|---|---|
| Reguły 1, 2: 5 szt., 100 równoczesnych kupujących | Zawsze 5 sukcesów i `reserved = 5`. Naiwny `SELECT`→`UPDATE` sprzedał 87–100. |
| Kolejność blokad: [X,Y] i [Y,X] | Z sortowaniem po ID 0 deadlocków na 20 przebiegów. Bez sortowania deadlock w 20 na 20 (przy wymuszonym przeplocie). |
| Reguła 4: wszystko albo nic | Rollback cofa już zarezerwowane pozycje. |
| Brak wiersza `stock_level` | 0 wierszy, bez wyjątku. Nieodróżnialne od braku towaru, zgodnie z PRD 5.3. |
| Reguła 5: anulowanie równolegle z rezerwacjami | Z warunkiem na statusie: jedno zwolnienie w 20 na 20. Naiwnie: wielokrotne zwolnienia w 17 na 20, do 21 rezerwacji na 5 szt. |
| `CHECK` | Odrzuca każdy zapis łamiący `0 <= reserved <= on_hand` (`23514`). |
| `REPEATABLE READ` / `SERIALIZABLE` | Bez overbookingu, ale 95–99 na 100 dostaje `40001` i sprzedaje się tylko 1–5 szt. |

## Ryzyka do uwzględnienia w planie

1. **Izolacja nie jest zapisana jako wymaganie.** Domyślna izolacja PostgreSQL i Springa to `READ COMMITTED`. Ktoś mógłby ją zmienić przez `@Transactional(isolation = …)` albo ustawienia puli, a wtedy wróciłyby fałszywe odmowy i pojawiłaby się potrzeba ponawiania prób. ADR 0001 odrzucił ponawianie przy optimistic locking. Warto rozważyć test, który przypina izolację.
2. **Anulowanie przez encję JPA to dokładnie naiwny wariant.** Odczyt encji, `if (status == RESERVED)` w Javie, potem `save()`. Implementacja musi użyć warunkowego `UPDATE` (np. `@Modifying @Query`) albo blokady wiersza zamówienia.
3. **`CHECK` nie łapie błędów logiki.** Przy lost update licznik zostaje w dozwolonym zakresie, a kłamie liczba zamówień. Testy reguł 1 i 5 muszą porównywać liczbę udanych zamówień z `reserved`, nie tylko sprawdzać `CHECK`.
4. **Pula połączeń w docelowym teście PRD 7.** Prototyp celowo omijał pulę. Test na pełnej aplikacji z HikariCP (domyślnie 10 połączeń) może wyjść zielony bez prawdziwej rywalizacji o wiersz. Trzeba dobrać rozmiar puli albo przyjąć, że test sprawdza mniej.
5. **Istnienie produktu (404) trzeba sprawdzać przed rezerwacją.** `UPDATE` go nie rozróżni od braku towaru.
6. **Nie sprawdzono:** reguły 6 (spójność z `StockMovement`), idempotencji (`Idempotency-Key`), działania przez JPA/Hibernate, wielu magazynów (poza zakresem v1).

## Co czeka na decyzję użytkowniczki

Proponowane zmiany w dokumentach. Niczego jeszcze nie wprowadziłam, użytkowniczka nie odpowiedziała:

- **PRD 6.1:** dopisać wymaganie `READ COMMITTED` (ryzyko 1) i mechanizm anulowania z warunkiem na statusie (ryzyko 2) jako mechanizm dla reguły 5.
- **ADR 0001:** dodać sekcję „Weryfikacja prototypem” z werdyktem, konsekwencją dla izolacji i odnośnikiem do brancha `prototype/stock-locking`.
- Zapisać w planie projektu zadania wynikające z ryzyk 2–4.

## Stan repo

- Branch `prototype/stock-locking` (od `main`) nie jest wypchnięty na `origin`. Nie mergować go do `main`. `main` ma dostać tylko zatwierdzone decyzje.
- Na branchu prototypu `pom.xml` ma zależności testowe `org.postgresql:postgresql` i `org.testcontainers:testcontainers-postgresql`. Wersje zarządza Spring Boot 4.1.2, Testcontainers 2.0.5. Docelowa implementacja i tak będzie ich potrzebować. Docker 20.10 lokalnie działa z Testcontainers 2.x.
- Bez commita: `docs/LEARNING_LOG.md` (plik użytkowniczki, częściowo zatwierdzony przez `git add`, nie ruszać), `handoff-prototype.md`, ten plik.

## Jak pracować z użytkowniczką

Zasady z `handoff-prototype.md`, sekcja „Jak pracować z użytkowniczką”, nadal obowiązują: po polsku, dokładne wyjaśnienia z tabelami krok po kroku, decyzje podejmuje sama (po jednym pytaniu z rekomendacją), commity bez `Co-Authored-By`, PR bez stopki.

## Sugerowane skille

- **`mattpocock-skills:domain-modeling`**: do zmian w PRD 6.1 i ADR 0001 (izolacja, mechanizm anulowania, sekcja weryfikacji).
- **`mattpocock-skills:grilling`**: jeśli planowanie ma przemaglować ryzyka 1–4, zanim wejdą do planu.
- **`mattpocock-skills:tdd`**: przy właściwej implementacji. Scenariusze z prototypu to gotowa lista testów integracyjnych dla reguł 1, 2, 4, 5.
