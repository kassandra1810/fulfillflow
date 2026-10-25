# Rezerwacja towaru przez warunkowy UPDATE z ustaloną kolejnością blokad

Status: accepted (2026-10-25)

Najważniejszą gwarancją v1 jest brak oversellingu przy równoczesnych zamówieniach (`reserved ≤ onHand`). Każdą pozycję zamówienia rezerwujemy jednym warunkowym `UPDATE stock_level SET reserved = reserved + :qty WHERE … AND on_hand - reserved >= :qty`: 1 zmieniony wiersz oznacza sukces, 0 — brak towaru. Sprawdzenie i zapis to jeden krok w bazie, więc nie ma okna między odczytem a zapisem, a PostgreSQL kolejkuje równoczesne zmiany tego samego wiersza. Wszystkie pozycje zamówienia idą w jednej transakcji (brak którejkolwiek → rollback całości), w kolejności posortowanej po ID produktu, żeby dwie transakcje nigdy nie blokowały tych samych wierszy w przeciwnej kolejności (brak deadlocków). Ostatnią linią obrony jest `CHECK (reserved >= 0 AND reserved <= on_hand)` w schemacie.

## Rozważane opcje

- **Optimistic locking (`@Version` + retry)** — odrzucone: przy dużej rywalizacji o jeden wiersz (test: 100 zamówień na 5 sztuk) prawie każde żądanie dostaje konflikt wersji; wymaga limitu prób, a po jego wyczerpaniu daje fałszywe odrzucenia mimo dostępnego towaru, i niestabilne testy.
- **Pessimistic locking (`SELECT … FOR UPDATE`)** — poprawne, ale więcej kodu (odczyt, sprawdzenie w Javie, zapis) i ten sam wymóg kolejności blokad; bez zysku względem warunkowego `UPDATE`.

## Konsekwencje

- Niezmiennik pilnuje baza, a nie kod domenowy w Javie — kto zmieni stan inną drogą (np. `setReserved` na encji), obejdzie gwarancję. Dlatego stan magazynowy zmienia się wyłącznie przez publiczne API modułu Inventory (PRD 5.5).
- Anulowanie musi zwalniać rezerwację symetrycznie (`reserved - :qty`) i w tej samej kolejności.
