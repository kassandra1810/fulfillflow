# Rezerwacja towaru przez warunkowy UPDATE z ustaloną kolejnością blokad

Status: accepted (2026-10-25; warunki dotyczące izolacji i anulowania dodane po prototypie 2026-10-27)

## Kontekst

Najważniejszą gwarancją v1 jest brak oversellingu przy równoczesnych zamówieniach: `reserved ≤ onHand` zawsze (PRD 6, reguły 1 i 2). Do tego rezerwacja wielopozycyjnego zamówienia musi być atomowa (reguła 4), a anulowanie ma zwolnić towar dokładnie raz (reguła 5). Wymaganie testowe z PRD 7: produkt z `onHand = 5`, 100 równoczesnych zamówień po 1 szt. → dokładnie 5 sukcesów i `reserved = 5`.

Mamy więc dużą rywalizację o jeden wiersz `stock_level` i zamówienia blokujące kilka wierszy naraz, co grozi deadlockami.

## Rozważane opcje

| Opcja | Jak działa | Ocena |
|---|---|---|
| **Optimistic locking** (`@Version` + retry) | Odczyt, sprawdzenie w Javie, zapis z warunkiem na wersji; konflikt → ponowienie | Odrzucona. Przy 100 żądaniach na jeden wiersz prawie każde dostaje konflikt wersji. Potrzebny limit prób, a po jego wyczerpaniu fałszywe odmowy mimo dostępnego towaru i niestabilne testy. |
| **Pessimistic locking** (`SELECT … FOR UPDATE`) | Blokada wiersza przy odczycie, sprawdzenie w Javie, zapis | Poprawna, ale więcej kodu (trzy kroki zamiast jednego) i ten sam wymóg kolejności blokad. Brak zysku względem warunkowego `UPDATE`. |
| **Warunkowy `UPDATE`** | `UPDATE … SET reserved = reserved + :qty WHERE … AND on_hand - reserved >= :qty` | **Wybrana.** |

## Decyzja

1. Każdą pozycję zamówienia rezerwujemy jednym warunkowym `UPDATE`:
   `UPDATE stock_level SET reserved = reserved + :qty WHERE product_id = :p AND warehouse_id = :w AND on_hand - reserved >= :qty`.
   1 zmieniony wiersz → zarezerwowano; 0 wierszy → brak towaru (także gdy wiersza `stock_level` nie ma, zgodnie z PRD 5.3).
2. Wszystkie pozycje zamówienia idą w jednej transakcji; pierwsza pozycja bez towaru → rollback całości.
3. Pozycje przetwarzamy w kolejności posortowanej po ID produktu.
4. Transakcje rezerwacji i anulowania działają w izolacji **`READ COMMITTED`** (domyślnej w PostgreSQL i Springu).
5. Anulowanie zmienia status warunkowym `UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND status = 'RESERVED'`. Towar (`reserved = reserved - :qty`, w tej samej kolejności co przy rezerwacji) zwalniamy w tej samej transakcji i tylko wtedy, gdy ten `UPDATE` zmienił 1 wiersz.
6. Ostatnia linia obrony w schemacie: `CHECK (reserved >= 0 AND reserved <= on_hand)`.

## Uzasadnienie

- Sprawdzenie i zapis to jeden krok w bazie, więc nie ma okna między odczytem a zapisem. PostgreSQL blokuje zmieniony wiersz do końca transakcji, a w `READ COMMITTED` `UPDATE`, który czekał na blokadę, ponownie sprawdza `WHERE` na świeżej wersji wiersza. Dlatego równoczesne rezerwacje ustawiają się w kolejce i każda widzi aktualny stan, bez ponawiania prób.
- Stała kolejność blokad sprawia, że dwie transakcje nigdy nie czekają na siebie nawzajem (brak deadlocków).
- Warunek na statusie przy anulowaniu robi z „sprawdź, czy `RESERVED`, i zmień” jeden atomowy krok, więc dwa równoczesne anulowania nie zwolnią towaru dwa razy.

## Weryfikacja prototypem

Branch `prototype/stock-locking` (commity `d6de108`, `aba3cdb`; nie mergować do `main`), test `ReservationPrototypeTest`: czyste JDBC, PostgreSQL 17 w Testcontainers, każdy scenariusz współbieżny 20 razy.

| Scenariusz | Wynik |
|---|---|
| 5 szt., 100 równoczesnych rezerwacji | Warunkowy `UPDATE`: zawsze 5 sukcesów, `reserved = 5`. Naiwny `SELECT` → `UPDATE`: 87–100 sukcesów. |
| Zamówienia [X,Y] i [Y,X] | Sortowanie po ID: 0 deadlocków na 20. Bez sortowania: deadlock 20 na 20. |
| Wszystko albo nic | Rollback cofa już zarezerwowane pozycje. |
| Anulowanie równoległe z rezerwacjami | Z warunkiem na statusie: jedno zwolnienie 20 na 20. Naiwnie: wielokrotne zwolnienia w 17 na 20, do 21 rezerwacji na 5 szt. |
| `CHECK` | Odrzuca każdy zapis łamiący `0 <= reserved <= on_hand` (`23514`). |
| `REPEATABLE READ` / `SERIALIZABLE` | Bez oversellingu, ale 95–99 na 100 dostaje `40001`; sprzedaje się tylko 1–5 szt. |

Wniosek: mechanizm działa pod warunkami z punktów 4 i 5 decyzji.

## Konsekwencje

- Niezmiennik pilnuje baza, a nie kod domenowy w Javie. Kto zmieni stan inną drogą (np. `setReserved` na encji), obejdzie gwarancję — dlatego stan magazynowy zmienia się wyłącznie przez `InventoryApi` (PRD 5.5).
- Poprawność zależy od poziomu izolacji, którego nie widać w kodzie rezerwacji. Zmiana przez `@Transactional(isolation = …)` lub ustawienia puli przywróci fałszywe odmowy. Warto mieć test, który przypina `READ COMMITTED`.
- Anulowanie przez encję JPA (odczyt, `if (status == RESERVED)` w Javie, `save()`) to dokładnie naiwny wariant z prototypu. Implementacja musi użyć warunkowego `UPDATE` (np. `@Modifying @Query`) albo blokady wiersza zamówienia.
- `CHECK` nie łapie błędów logiki: przy lost update licznik zostaje w dozwolonym zakresie, a nie zgadza się liczba zamówień. Testy reguł 1 i 5 porównują liczbę udanych zamówień z `reserved`.
- `UPDATE` nie odróżnia nieistniejącego produktu od braku towaru — istnienie produktu (404) sprawdzamy przed rezerwacją.
- Test z PRD 7 na pełnej aplikacji idzie przez pulę HikariCP (domyślnie 10 połączeń); trzeba dobrać jej rozmiar, inaczej test nie wytworzy prawdziwej rywalizacji o wiersz.
