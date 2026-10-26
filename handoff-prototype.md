# Handoff: prototyp mechanizmu rezerwacji

## Pytanie, na które ma odpowiedzieć prototyp

**Czy mechanizm rezerwacji towaru wybrany w PRD naprawdę zapobiega overbookingowi?**

Mechanizm i uzasadnienie są opisane w:
- `docs/PRD.md`, sekcja **6.1**: warunkowy `UPDATE … WHERE on_hand - reserved >= :qty`, jedna transakcja na zamówienie, pozycje posortowane po ID produktu, `CHECK (reserved >= 0 AND reserved <= on_hand)`,
- `docs/adr/0001-reservation-conditional-update.md`: decyzja, odrzucone alternatywy i konsekwencje,
- `docs/PRD.md`, sekcje **6** (niezmienniki 1–6) i **7** (test współbieżności: `onHand = 5`, 100 równoczesnych zamówień po 1 szt. → dokładnie 5 `RESERVED`, `reserved = 5`).

Tych dokumentów nie powielam. Prototyp ma je **zweryfikować**, a nie zaprojektować mechanizm od nowa.

## Co prototyp powinien sprawdzić

Każdy scenariusz uruchomić wielokrotnie, bo błędy współbieżności są niedeterministyczne:

1. **Scenariusz z PRD 7:** 5 sztuk, 100 równoczesnych zamówień po 1 szt. Oczekiwany wynik: dokładnie 5 sukcesów, `reserved = 5`, nigdy więcej.
2. **Kontrprzykład:** ten sam scenariusz z naiwnym „SELECT, sprawdź w Javie, UPDATE”. Prototyp powinien pokazać, że naiwna wersja **faktycznie** sprzedaje za dużo. Bez tego zielony wynik z punktu 1 niewiele dowodzi.
3. **Wielopozycyjne zamówienia w przeciwnej kolejności** ([X, Y] i [Y, X] równocześnie):
   - **bez** sortowania: czy pojawiają się deadlocki (`deadlock detected` w PostgreSQL)?
   - **z** sortowaniem po ID produktu: czy deadlocki znikają?
4. **Wszystko albo nic:** zamówienie [X: dostępne, Y: brak]. Po odrzuceniu `reserved` dla X musi wrócić do stanu sprzed zamówienia.
5. **Anulowanie równolegle z rezerwacją:** stan końcowy zgodny z regułą 5 (zwolnienie dokładnie raz).
6. **Linia obrony `CHECK`:** ręczny zapis łamiący `reserved <= on_hand` musi zostać odrzucony przez bazę.

## Rzeczy do świadomego zweryfikowania (nie zakładać)

- **Poziom izolacji.** Mechanizm zakłada domyślny `READ COMMITTED` w PostgreSQL. W tym trybie `UPDATE`, który czekał na blokadę wiersza, **ponownie sprawdza `WHERE` na świeżej wersji wiersza** i na tym opiera się poprawność. Warto sprawdzić, co się dzieje przy `REPEATABLE READ`. Prawdopodobnie pojawi się błąd serializacji zamiast poprawnego „0 wierszy”, a to by znaczyło, że izolację trzeba zapisać w PRD lub ADR jako wymaganie.
- **Pula połączeń kontra 100 wątków.** Przy małej puli (np. domyślne 10 w HikariCP) żądania ustawiają się w kolejce do puli, a nie do bazy, więc test może wyjść zielony bez prawdziwej równoczesności na wierszu. Prototyp powinien wymusić rzeczywistą równoczesność, np. przez `CountDownLatch` i pulę ≥ liczba wątków albo przez bezpośrednie połączenia JDBC.
- **Brak wiersza `stock_level`** (produkt nigdy nieprzyjęty): `UPDATE` zwraca 0 wierszy, co zgodnie z PRD 5.3 traktujemy jako brak towaru. Potwierdzić, że nie da się tego pomylić z innym błędem.

## Stan środowiska (2026-10-26)

- Repo: `platform-api/` to szkielet Spring Boot 4.1.2 / Java 25. W `pom.xml` nie ma jeszcze JPA, PostgreSQL, Flyway ani Testcontainers. Kodu domenowego brak.
- Lokalnie: JDK 25 i Docker (server 20.10.23) są dostępne, więc PostgreSQL przez Testcontainers albo `docker run postgres` zadziała. `psql` nie jest zainstalowany.
- Aktualny branch: `docs/prd-v1-decisions` (PRD i ADR, niezmergowane). Prototyp robić na osobnym branchu od niego albo od `main` po merge.
- `docs/LEARNING_LOG.md` to plik użytkowniczki, częściowo zatwierdzony przez `git add`. Nie ruszać i nie commitować.

## Zasady dla prototypu

- To **prototyp do wyrzucenia**: odpowiada na jedno pytanie i nie jest początkiem właściwej implementacji. Trzymać go osobno od kodu produkcyjnego, np. w osobnym katalogu `prototypes/reservation/` albo w pakiecie testowym, żeby nie mieszał się z modułami z PRD 5.5.
- Wynik: krótki raport (tak/nie i dowody: liczby z powtórzonych uruchomień, przykładowe logi deadlocków). Jeśli wyjdzie coś nowego (np. wymaganie co do izolacji), zaproponować zmianę w `docs/PRD.md` 6.1 lub w ADR 0001, ale **nie wprowadzać jej bez zgody**.

## Jak pracować z użytkowniczką

- Rozmowa po polsku.
- Lubi dokładne wyjaśnienia trudnych pojęć (blokady, deadlocki, granice modułów) z przykładami i tabelami „krok po kroku”. Dopytuje, gdy coś jest niejasne.
- Decyzje podejmuje sama: pytać po jednym pytaniu, z rekomendacją.
- **Commity bez linii `Co-Authored-By: Claude`**, a opisy PR bez stopki „Generated with Claude Code”.
- `gh` nie jest zainstalowane. Na później czeka też lista spraw GitHuba (PR, ochrona `main`, poprawka dwóch commitów), ale nie jest częścią tej sesji.

## Sugerowane skille

- **`mattpocock-skills:prototype`**: główny skill tej sesji (prototyp do wyrzucenia, odpowiadający na jedno pytanie projektowe).
- **`mattpocock-skills:tdd`**: opcjonalnie, jeśli scenariusze z listy wyżej mają powstać jako testy integracyjne (najpierw czerwony test dla naiwnej wersji, potem zielony dla mechanizmu z 6.1).
- **`mattpocock-skills:domain-modeling`**: tylko jeśli prototyp wymusi zmianę PRD lub ADR (np. dopisanie wymagania co do poziomu izolacji).
