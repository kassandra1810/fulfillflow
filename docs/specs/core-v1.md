# FulfillFlow Core v1 — specyfikacja (Catalog, Inventory, Orders)

> Źródła: [PRD](../PRD.md) (stan z 2026-10-25/27) i [ADR 0001](../adr/0001-stock-reservation-locking.md).
> Spec nie wprowadza nic spoza zakresu v1. Decyzje doprecyzowane przy pisaniu specyfikacji (2026-10-28) są oznaczone **[SPEC]**.

## Problem

Mała firma sprzedająca online prowadzi stany magazynowe w arkuszu. Gdy kilka zamówień wpada jednocześnie, sprzedaje towar, którego już nie ma (overselling), a anulowane zamówienia nie zwalniają towaru. Nie ma jednego miejsca, które mówi prawdę o produktach, stanach i zamówieniach, i nie ma gwarancji, że sklep nie sprzeda więcej, niż ma.

## Rozwiązanie

Backend z REST API (`/api/v1`, JSON, błędy jako Problem Details RFC 9457, dokumentacja OpenAPI), który jest jedynym źródłem prawdy dla trzech obszarów:

- **Catalog** — operator tworzy, edytuje i przegląda produkty identyfikowane unikalnym `sku`, z ceną w PLN.
- **Inventory** — magazynier przyjmuje towar do magazynu domyślnego, sprawdza stan (`onHand`, `reserved`, `available`) i historię ruchów.
- **Orders** — system sprzedażowy składa zamówienie, które albo rezerwuje cały towar, albo zostaje odrzucone (wszystko albo nic); może bezpiecznie ponowić złożenie zamówienia (`Idempotency-Key`) i anulować zamówienie, co zwalnia rezerwację dokładnie raz.

Równoczesne zamówienia nigdy nie rezerwują więcej, niż jest dostępne. Gwarancję daje baza danych (warunkowy `UPDATE`, ADR 0001), a nie kod domenowy.

## User stories

### Catalog

1. Jako operator katalogu chcę utworzyć produkt z `sku`, nazwą, opcjonalnym opisem i ceną, aby można go było przyjmować na magazyn i zamawiać.
2. Jako operator katalogu chcę dostać 409, gdy tworzę produkt z `sku`, które już istnieje, aby w katalogu nie było dwóch produktów o tym samym kodzie.
3. Jako operator katalogu chcę dostać 400 z opisem pól, gdy nazwa jest pusta, cena jest ≤ 0 albo ma więcej niż 2 miejsca po przecinku, aby błędne dane nigdy nie trafiły do katalogu i nic nie było cicho zaokrąglane.
4. Jako operator katalogu chcę, aby cena była przechowywana jako kwota dziesiętna (nie `double`), aby kwoty były dokładne.
5. Jako operator katalogu chcę pobrać produkt po `id` i zobaczyć jego `sku`, nazwę, opis, cenę i `version`, aby wiedzieć, co edytuję.
6. Jako operator katalogu chcę dostać 404, gdy pobieram produkt o nieistniejącym `id`.
7. Jako operator katalogu chcę przeglądać listę produktów z paginacją, posortowaną po `sku`, aby katalog dało się czytać niezależnie od jego rozmiaru; nieprawidłowe `page`/`size` dają 400.
8. Jako system sprzedażowy chcę znaleźć produkt po SKU przez `GET /products?sku=…` (strona z 0 albo 1 elementem), aby poznać jego `id` potrzebne do zamówienia.
9. Jako operator katalogu chcę zmienić nazwę, opis i cenę produktu przez `PATCH`, przesyłając tylko zmieniane pola, aby nie musieć odsyłać całego produktu.
10. Jako operator katalogu chcę wyczyścić opis, wysyłając `"description": null`, a pominięcie pola ma zostawić opis bez zmian.
11. Jako operator katalogu chcę dostać 400, gdy w `PATCH` przesyłam `sku`, bo `sku` jest niezmienne.
12. Jako operator katalogu chcę, aby `PATCH` walidował nazwę i cenę tak samo jak tworzenie produktu.
13. Jako operator katalogu chcę, aby `PATCH` wymagał pola `version` (brak → 400), a niezgodna wersja kończyła się 409, aby nie nadpisać zmiany zrobionej w międzyczasie przez kogoś innego.
14. Jako operator katalogu chcę, aby każda udana edycja zmieniała `version`, aby kolejna edycja na starych danych została wykryta.
15. Jako operator katalogu chcę, aby zmiana ceny nie zmieniała cen w już złożonych zamówieniach.

### Inventory

16. Jako magazynier chcę przyjąć towar (`productId`, ilość > 0) do magazynu domyślnego, aby zwiększyć `onHand`.
17. Jako magazynier chcę dostać 400, gdy ilość przyjęcia nie jest dodatnią liczbą całkowitą.
18. Jako magazynier chcę dostać 404, gdy przyjmuję towar dla nieistniejącego produktu.
19. Jako magazynier chcę, aby przyjęcie towaru miało własny identyfikator, do którego odwołuje się wpis w historii.
20. Jako magazynier chcę odczytać stan produktu (`onHand`, `reserved`, `available`), aby wiedzieć, ile mogę jeszcze sprzedać.
21. Jako magazynier chcę, aby produkt nigdy nieprzyjęty na magazyn miał stan `0 / 0 / 0`, a nie błąd.
22. Jako magazynier chcę dostać 404, gdy pytam o stan nieistniejącego produktu.
23. Jako magazynier chcę przeglądać historię ruchów produktu (`RECEIPT`, `RESERVATION`, `RELEASE`) z paginacją, od najnowszych, aby zrozumieć, skąd wziął się aktualny stan.
24. Jako magazynier chcę, aby każdy wpis historii miał typ, produkt, magazyn, dodatnią ilość, czas i odnośnik do źródła (`stockReceiptId` albo `orderId`).
25. Jako magazynier chcę, aby historia była tylko do dopisywania, aby była wiarygodnym zapisem zmian.
26. Jako magazynier chcę, aby stan zawsze zgadzał się z historią: `onHand` = suma `RECEIPT`, `reserved` = suma `RESERVATION` − suma `RELEASE`.
27. Jako magazynier chcę, aby API nie wymagało ani nie pokazywało magazynu, bo w v1 jest tylko magazyn domyślny.

### Orders

28. Jako system sprzedażowy chcę złożyć zamówienie z co najmniej jedną pozycją (`productId`, ilość > 0) i dostać 201 z zamówieniem w statusie `RESERVED`, aby towar był zablokowany dla kupującego.
29. Jako system sprzedażowy chcę dostać 400, gdy zamówienie nie ma pozycji, ilość nie jest dodatnią liczbą całkowitą albo ten sam produkt występuje w więcej niż jednej linii.
30. Jako system sprzedażowy chcę dostać 404 ze wskazaniem brakującego ID, gdy linia wskazuje nieistniejący produkt.
31. Jako system sprzedażowy chcę dostać 409 z listą wszystkich brakujących pozycji (`sku`, `requested`, `available`), gdy którejkolwiek pozycji brakuje, aby w jednej odpowiedzi wiedzieć, co poprawić.
32. Jako system sprzedażowy chcę, aby odrzucone zamówienie nie zarezerwowało niczego, nawet jeśli części pozycji wystarczało (wszystko albo nic).
33. Jako system sprzedażowy chcę, aby odrzucone zamówienie nie było zapisywane, aby nie zaśmiecało danych.
34. Jako system sprzedażowy chcę, aby produkt bez stanu był traktowany jak `available = 0` (409), a nie jak błąd serwera.
35. Jako system sprzedażowy chcę, aby cena jednostkowa każdej linii była kopiowana z produktu w chwili zamówienia, aby zamówienie nie zmieniało się po zmianie ceny.
36. Jako system sprzedażowy chcę wysłać opcjonalny nagłówek `Idempotency-Key`, aby po timeoucie bezpiecznie ponowić `POST` bez ryzyka drugiego zamówienia.
37. Jako system sprzedażowy chcę przy ponowieniu z tym samym kluczem i tą samą treścią dostać 200 z istniejącym zamówieniem, bez drugiej rezerwacji.
38. Jako system sprzedażowy chcę, aby ta sama treść w innej kolejności linii była traktowana jako ta sama treść.
39. Jako system sprzedażowy chcę dostać 422, gdy używam tego samego klucza z inną treścią, aby wykryć błąd po swojej stronie.
40. Jako system sprzedażowy chcę, aby dwa równoczesne żądania z tym samym kluczem utworzyły tylko jedno zamówienie, a drugie dostało 200 z istniejącym zamówieniem i nie zostawiło rezerwacji.
41. Jako system sprzedażowy chcę, aby odrzucenie z braku towaru nie zapamiętywało klucza, aby ponowienie po dostawie mogło się udać.
42. Jako system sprzedażowy chcę złożyć zamówienie bez nagłówka `Idempotency-Key` i mieć je utworzone normalnie.
43. Jako system sprzedażowy chcę pobrać zamówienie po `id` i zobaczyć jego status i linie (produkt, ilość, cena jednostkowa).
44. Jako system sprzedażowy chcę dostać 404 dla nieistniejącego zamówienia.
45. Jako system sprzedażowy chcę anulować zamówienie, aby zwolnić jego rezerwację i przywrócić `available`.
46. Jako system sprzedażowy chcę, aby ponowne anulowanie tego samego zamówienia zakończyło się sukcesem i nie zwolniło towaru drugi raz.
47. Jako system sprzedażowy chcę, aby dwa równoczesne anulowania zwolniły towar dokładnie raz.
48. Jako system sprzedażowy chcę dostać 404 przy anulowaniu nieistniejącego zamówienia.

### Gwarancje współbieżności i spójności

49. Jako właściciel sklepu chcę, aby przy 5 sztukach i 100 równoczesnych zamówieniach po 1 szt. powstało dokładnie 5 zamówień `RESERVED`, 95 odrzuconych i `reserved = 5`, aby nigdy nie sprzedać więcej, niż mam.
50. Jako właściciel sklepu chcę, aby sklep nie odmawiał sprzedaży, gdy towar jest dostępny (brak fałszywych odmów pod obciążeniem).
51. Jako właściciel sklepu chcę, aby zamówienia z tymi samymi produktami w różnej kolejności nie powodowały deadlocków.
52. Jako właściciel sklepu chcę, aby baza odrzuciła każdy zapis łamiący `0 ≤ reserved ≤ onHand`, jako ostatnią linię obrony.

### Developer / operacje

53. Jako developer chcę, aby złamanie granic modułów albo cykl zależności dawały czerwony build.
54. Jako developer chcę, aby schemat bazy zmieniał się wyłącznie migracjami Flyway, a magazyn domyślny był tworzony migracją.
55. Jako developer chcę uruchomić aplikację i testy tylko według README.
56. Jako developer chcę dokumentację OpenAPI wszystkich endpointów.

## Decyzje implementacyjne

### Moduły i granice (PRD 5.5)

- Modularny monolit: jedna aplikacja, trzy moduły jako pakiety najwyższego poziomu `catalog`, `inventory`, `orders`.
- Każdy moduł wystawia mały publiczny interfejs w pakiecie głównym modułu; encje, repozytoria i implementacja są w podpakietach wewnętrznych. Między modułami przechodzą tylko ID i proste rekordy, nigdy encje.
- Kierunek zależności: `orders → catalog`, `orders → inventory`, `inventory → catalog`. `catalog` nie zależy od nikogo. Brak cykli.
- Granice pilnuje Spring Modulith (`ApplicationModules.of(...).verify()` w `./mvnw verify`).
- Nowe zależności w stosie (PRD 7): PostgreSQL, Spring Data JPA / Hibernate, Flyway, Spring Modulith, generator OpenAPI, Testcontainers.

### Publiczne interfejsy modułów

- **`CatalogApi`** — odczyt danych produktów potrzebnych innym modułom: istnienie produktu oraz prosty rekord z `id`, `sku` i `price` dla podanego zestawu ID. Inventory używa go do 404 przy przyjęciu i odczycie stanu; Orders — do 404 dla brakujących produktów, do kopiowania ceny i do `sku` w odpowiedzi 409.
- **`InventoryApi`** — jedyna droga zmiany stanu magazynowego:
  - przyjęcie towaru (produkt, ilość) → identyfikator `StockReceipt`,
  - odczyt stanu produktu (`onHand`, `reserved`, `available`),
  - rezerwacja pozycji zamówienia (ID zamówienia, lista produkt × ilość) → sukces albo lista braków (`productId`, `requested`, `available`),
  - zwolnienie rezerwacji (ID zamówienia, lista produkt × ilość),
  - odczyt historii ruchów produktu z paginacją.
  Rezerwacja i zwolnienie uczestniczą w transakcji wywołującego (Orders), nie otwierają własnej.

### Model danych

- Wszystkie encje mają identyfikator UUIDv7, nadawany w aplikacji przed zapisem (PRD 5.4).
- `Product`: `id`, `sku` (unikalny indeks), `name`, `description` (nullable), `price` (decimal, skala 2, `> 0`), `version`.
- `Warehouse`: `id`; jeden wiersz — magazyn domyślny — tworzony migracją.
- `StockLevel`: per produkt × magazyn; `on_hand`, `reserved`; ograniczenie `CHECK (reserved >= 0 AND reserved <= on_hand)`. Wiersz powstaje przy pierwszym przyjęciu towaru.
- `StockReceipt`: `id`, produkt, magazyn, ilość, czas.
- `StockMovement`: `id`, typ (`RECEIPT` / `RESERVATION` / `RELEASE`), produkt, magazyn, ilość (dodatnia), czas, `stockReceiptId` albo `orderId`. Tylko do dopisywania.
- `Order`: `id`, `status` (`RESERVED` / `CANCELLED`), `idempotencyKey` (nullable, unikalny indeks), dane potrzebne do porównania treści przy ponowieniu, czas utworzenia.
- `OrderLine`: zamówienie, produkt, ilość, cena jednostkowa skopiowana w chwili zamówienia.
- Powiązania `OrderLine` i `StockMovement` z `Product` nie mają kaskadowego usuwania; produkty nie są usuwane.

### Kontrakt API (prefiks `/api/v1`)

| Metoda i ścieżka | Wynik | Błędy |
|---|---|---|
| `POST /products` | 201, produkt | 400 walidacja; 409 duplikat `sku` |
| `GET /products` (paginacja, opcjonalny `?sku=`) | 200, strona produktów | 400 |
| `GET /products/{id}` | 200, produkt z `version` | 404 |
| `PATCH /products/{id}` | 200, produkt z nową `version` | 400 walidacja / `sku` w treści / brak `version`; 404; 409 niezgodna `version` |
| `POST /stock-receipts` **[SPEC]** | 201, przyjęcie (`id`, `productId`, `quantity`) | 400; 404 produkt |
| `GET /products/{id}/stock` **[SPEC]** | 200, `onHand`, `reserved`, `available` | 404 produkt |
| `GET /products/{id}/stock-movements` (paginacja) | 200, strona ruchów | 404 produkt |
| `POST /orders` (opcjonalny `Idempotency-Key`) | 201 nowe; 200 ponowienie z tym samym kluczem i treścią | 400; 404 produkt (wskazane ID); 409 brak towaru; 422 klucz z inną treścią |
| `GET /orders/{id}` | 200, zamówienie ze statusem i liniami | 404 |
| `POST /orders/{id}/cancel` **[SPEC]** | 200, zamówienie w statusie `CANCELLED` (także przy ponownym anulowaniu) | 404 |

- Wszystkie błędy w formacie Problem Details. 400 zawiera opis pól.
- 409 z braku towaru zawiera listę brakujących pozycji: `sku`, `requested`, `available` (migawka z chwili odrzucenia).
- Cena w API to sama kwota (bez pola `currency`).
- API nie przyjmuje ani nie zwraca magazynu.
- **[SPEC]** `PATCH /products/{id}`: `version` jest polem w treści JSON obok `name`, `description`, `price`; pole jest wymagane.
- **[SPEC]** `GET /products/{id}/stock` dla istniejącego produktu, którego nigdy nie przyjęto, zwraca 200 z `onHand = 0`, `reserved = 0`, `available = 0`. 404 tylko dla nieistniejącego produktu (sprawdzane przez `CatalogApi`). Brak wiersza `stock_level` to szczegół przechowywania, nie błąd API; wiersz nie jest tworzony przy tworzeniu produktu, bo `catalog` nie może zależeć od `inventory`.

### Paginacja [SPEC]

Dotyczy `GET /products` i `GET /products/{id}/stock-movements`.

- Paginacja stronami: `?page=0&size=20`; `page` od 0, domyślnie `size = 20`, maksymalnie `100`.
- `page < 0`, `size < 1` lub `size > 100` → 400 (bez cichego przycinania).
- Klient nie wybiera sortowania. Kolejność jest stała: produkty po `sku` rosnąco; ruchy magazynowe od najnowszych (czas malejąco, przy remisie `id` malejąco).
- Kształt odpowiedzi (stabilny format DTO ze Spring Data, nie serializacja `Page`/`PageImpl`):

  ```json
  {
    "content": [ ... ],
    "page": { "number": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
  }
  ```

- `GET /products?sku=…` zwraca tę samą stronę z 0 albo 1 elementem, nigdy 404.

### Edycja produktu (PRD 5.1)

- Semantyka `PATCH`: pominięte pole bez zmian; `"description": null` czyści opis; `"name": null` lub `"price": null` to błąd walidacji; `sku` w treści → 400.
- Wersjonowanie optymistyczne na `Product.version`; niezgodna wersja → 409.

### Rezerwacja (PRD 6.1, ADR 0001)

- Każda linia rezerwowana jednym warunkowym `UPDATE`:
  `UPDATE stock_level SET reserved = reserved + :qty WHERE product_id = :p AND warehouse_id = :w AND on_hand - reserved >= :qty`.
  1 wiersz → zarezerwowano; 0 wierszy → brak towaru (także gdy wiersza `stock_level` nie ma).
- Linie przetwarzane w kolejności posortowanej po ID produktu.
- Wszystkie linie, zapis zamówienia i wpisy `RESERVATION` (po jednym na linię) w jednej transakcji; pierwszy brak → rollback całości.
- **[SPEC]** 409 wymienia wszystkie brakujące pozycje, nie tylko pierwszą. Pętla nie przerywa się na pierwszym braku: warunkowy `UPDATE` wykonujemy dla każdej linii (w kolejności po ID produktu), linie z 0 zmienionych wierszy zbieramy, dla każdej odczytujemy migawkę `available` (brak wiersza `stock_level` → 0), po czym cała transakcja jest wycofywana i zwracamy 409. Atomowość i kolejność blokad bez zmian.
- Istnienie wszystkich produktów (404) sprawdzane przed rezerwacją, bo `UPDATE` nie odróżnia braku produktu od braku towaru.
- Izolacja transakcji rezerwacji i anulowania: `READ COMMITTED`. Nie wolno jej zmieniać.

### Anulowanie (PRD 6.1, ADR 0001)

- Status zmieniany warunkowym `UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND status = 'RESERVED'`.
- Tylko gdy zmienił 1 wiersz: w tej samej transakcji `reserved = reserved - :qty` dla każdej linii, w tej samej kolejności co przy rezerwacji, i po jednym wpisie `RELEASE` na linię.
- 0 zmienionych wierszy i zamówienie istnieje → sukces bez zmian (idempotencja); zamówienie nie istnieje → 404.
- Zakazane: odczyt encji, sprawdzenie statusu w Javie i `save()` (naiwny wariant z prototypu).

### Idempotencja tworzenia zamówienia (PRD 5.3)

- Klucz zapisywany przy zamówieniu, unikalny indeks, pamiętany bezterminowo.
- **[SPEC]** „Ta sama treść” = ten sam zbiór par (`productId`, `quantity`), niezależnie od kolejności linii.
- Ponowienie z tym samym kluczem i tą samą treścią → 200 z istniejącym zamówieniem w jego aktualnym stanie, bez rezerwacji.
- Ten sam klucz, inna treść → 422.
- Równoczesne żądania z tym samym kluczem: unikalny indeks przepuszcza jedno; drugie wycofuje swoją transakcję (w tym rezerwację) i zwraca istniejące zamówienie (200).
- Odrzucenie z braku towaru nie zapisuje klucza.
- Bez nagłówka — brak ochrony przed duplikatem.

### Statusy zamówienia

```
RESERVED ──cancel──▶ CANCELLED
```

`RESERVED` nie wygasa i nie ma kroku wydania. Odrzucone zamówienia nie są zapisywane (brak `REJECTED`).

## Decyzje testowe

### Co to jest dobry test

- Test sprawdza zachowanie widoczne z zewnątrz: kody HTTP, treść odpowiedzi, stan odczytany przez API. Nie sprawdza encji, repozytoriów ani wywołań wewnętrznych.
- Reguły współbieżności sprawdzamy prawdziwą rywalizacją (wiele wątków, prawdziwy PostgreSQL), nie mockami.
- Testy reguł 1 i 5 porównują liczbę udanych zamówień z `reserved` (`CHECK` nie wykryje lost update).

### Seamy

1. **HTTP API `/api/v1` na pełnej aplikacji** (Spring Boot + PostgreSQL w Testcontainers) — główny i jedyny seam testów zachowania: walidacja, kody błędów, edycja z `version`, przyjęcia, stan, historia, zamówienia, idempotencja, anulowanie, współbieżność.
2. **Spring Modulith** — test `ApplicationModules.of(...).verify()` pilnujący granic i braku cykli.
3. **Przypięcie izolacji** — test, który pada, gdy transakcje rezerwacji/anulowania działają w izolacji innej niż `READ COMMITTED` (ADR 0001, Konsekwencje).

### Co testujemy (każda reguła z PRD 6 ma test)

- Reguła 1–2: test z PRD 7 — `onHand = 5`, 100 równoczesnych zamówień po 1 szt. → dokładnie 5 × 201 (`RESERVED`), 95 × 409, `reserved = 5`, `available = 0`. Uruchamiany stabilnie wielokrotnie.
- Reguła 3: ilości ≤ 0 i niecałkowite w przyjęciu i zamówieniu → 400.
- Reguła 4: zamówienie wielopozycyjne z jedną brakującą pozycją → 409, stan pozostałych pozycji bez zmian, brak wpisów `RESERVATION`.
- Reguła 5: ponowne anulowanie i równoczesne anulowania → `reserved` zmniejszony dokładnie raz, jeden wpis `RELEASE` na linię.
- Reguła 6: po scenariuszu z przyjęciami, rezerwacjami i anulowaniami stan zgadza się z sumami z historii ruchów.
- Brak deadlocków: równoczesne zamówienia [X, Y] i [Y, X] kończą się bez błędu serwera.
- Zmiana ceny po złożeniu zamówienia nie zmienia ceny w `OrderLine` (PRD 5.1).
- Idempotencja: 201 → 200 przy ponowieniu (także z inną kolejnością linii), 422 przy innej treści, równoczesne żądania z tym samym kluczem → jedno zamówienie i jedna rezerwacja, ponowienie po 409 może się udać.
- Kody błędów z PRD 7: 400, 404, 409 (`sku`, brak towaru, `version`), 422.
- 409 z braku towaru przy kilku brakujących liniach wymienia każdą z nich (`sku`, `requested`, `available`).
- Stan produktu nigdy nieprzyjętego → 200 z `0 / 0 / 0`.
- Paginacja: domyślne wartości, stała kolejność, kształt odpowiedzi, 400 dla nieprawidłowych `page`/`size`, `?sku=` bez wyników → pusta strona.

### Prior art

- W `main` jest tylko test kontekstu Spring (`@SpringBootTest`).
- Branch `prototype/stock-locking` (`ReservationPrototypeTest`) ma scenariusze współbieżne na czystym JDBC z PostgreSQL 17 w Testcontainers — wzorzec dla scenariuszy, nie kod do przeniesienia.
- Test z PRD 7 przez pełną aplikację idzie przez pulę HikariCP (domyślnie 10 połączeń); rozmiar puli w testach trzeba dobrać tak, by powstała prawdziwa rywalizacja o wiersz.

## Poza zakresem

Płatności; wysyłka i realizacja zamówień; zapis i statystyki odrzuconych zamówień (status `REJECTED`); wiele magazynów i wybór magazynu; wygasanie rezerwacji; wydanie towaru (`SHIPPED`); uwierzytelnianie i role; wiele walut (pole `currency`); multi-tenancy; usuwanie produktów i statusy `ACTIVE`/`INACTIVE`; zmiana `sku`; lista zamówień i wyszukiwanie zamówień; Kafka i zdarzenia; osobne serwisy; frontend; monitoring; AWS/Terraform.

## Dalsze uwagi

- Decyzje **[SPEC]** podjęte 2026-10-28: ścieżki `POST /stock-receipts`, `GET /products/{id}/stock`, `POST /orders/{id}/cancel`; `version` jako pole w treści `PATCH`; „ta sama treść” przy `Idempotency-Key` = zbiór linii bez względu na kolejność; seamy testów jak wyżej.
- Potwierdzone 2026-10-28: stan produktu nigdy nieprzyjętego to `0 / 0 / 0`; 409 z braku towaru zbiera wszystkie brakujące linie (doprecyzowane też w ADR 0001 i PRD 6.1); paginacja stronami jak wyżej.
- Paginacja kursorem (keyset) dla historii ruchów to kandydat na v2, jeśli historia urośnie.
- Najważniejsze ryzyko implementacji: anulowanie przez encję JPA i zmiana izolacji transakcji po cichu łamią gwarancje z ADR 0001 — oba przypadki mają mieć test.
