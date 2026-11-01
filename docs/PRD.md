# FulfillFlow Core (v1) — Product Requirements Document

> Status: **doprecyzowany w sesji `/grill-with-docs` (2026-10-25)**.
> Punkty oznaczone **[DECYZJA]** zostały potwierdzone. Wszystkie pytania z sekcji „Otwarte pytania” są rozstrzygnięte.

## 1. Problem

Mała firma sprzedająca online prowadzi stany magazynowe w arkuszu. Przy kilku zamówieniach składanych jednocześnie zdarza się sprzedaż towaru, którego już nie ma (overselling), a anulowane zamówienia nie zwalniają towaru. Firma potrzebuje backendu, który jest jedynym źródłem prawdy o produktach, stanach i zamówieniach oraz gwarantuje, że nie sprzeda więcej, niż ma.

## 2. Cel v1

Działające REST API dla trzech obszarów: **Catalog**, **Inventory**, **Orders**, w którym:
- produkty można tworzyć, edytować i przeglądać,
- towar można przyjmować do magazynu i sprawdzać jego stan,
- zamówienie rezerwuje towar albo zostaje odrzucone,
- równoczesne zamówienia nigdy nie rezerwują więcej towaru, niż jest dostępne,
- anulowanie zamówienia zwalnia rezerwację.

## 3. Użytkownicy (aktorzy)

W v1 nie ma uwierzytelniania; aktorzy są pojęciowi i pomagają opisać przypadki użycia.

| Aktor | Co robi |
|---|---|
| Operator katalogu | Dodaje, edytuje i przegląda produkty |
| Magazynier | Przyjmuje towar, sprawdza stany i historię ruchów |
| System sprzedażowy (klient API) | Składa i anuluje zamówienia w imieniu kupujących |

## 4. Słownik domeny (ubiquitous language)

Nazwy w kodzie będą po angielsku; ten słownik jest wiążący dla nazewnictwa.

| Termin (kod) | Znaczenie |
|---|---|
| `Product` | Pozycja w katalogu, identyfikowana unikalnym `sku` |
| `Sku` | Unikalny kod produktu nadawany przez operatora |
| `version` | Numer wersji produktu; zmienia się przy każdej edycji, służy do wykrywania równoczesnych zmian |
| `price` | Cena produktu: kwota w PLN, dokładnie 2 miejsca po przecinku, zawsze > 0 |
| `Warehouse` | Fizyczny magazyn; w v1 istnieje tylko magazyn domyślny |
| `StockLevel` | Stan produktu w magazynie: `onHand`, `reserved` |
| `onHand` | Liczba sztuk fizycznie w magazynie |
| `reserved` | Liczba sztuk zarezerwowanych przez aktywne zamówienia |
| `available` | `onHand − reserved`; tylko to można zarezerwować |
| `StockReceipt` | Przyjęcie towaru do magazynu (zwiększa `onHand`) |
| `StockMovement` | Wpis w historii zmian stanu, tylko do dopisywania; typ `RECEIPT` (przyjęcie), `RESERVATION` (rezerwacja) lub `RELEASE` (zwolnienie) |
| `Order` | Zamówienie złożone przez system sprzedażowy |
| `OrderLine` | Pozycja zamówienia: produkt, ilość, cena jednostkowa w chwili zamówienia |
| `Idempotency-Key` | Klucz nadawany przez klienta API dla jednej intencji złożenia zamówienia; ponowienie z tym samym kluczem nie tworzy drugiego zamówienia |
| `Reservation` | Zablokowanie dostępnych sztuk na potrzeby zamówienia |

## 5. Zakres funkcjonalny v1

### 5.1 Catalog
- Utworzenie produktu: `sku`, nazwa, opcjonalny opis, cena.
- `sku` jest unikalne; próba utworzenia duplikatu kończy się błędem.
- Lista produktów z paginacją; filtr `?sku=` do wyszukania produktu po SKU.
- Pobranie produktu po identyfikatorze.
- **[DECYZJA]** Cena w jednej walucie (PLN), przechowywana jako kwota dziesiętna, nie `double`.
  - Dokładnie 2 miejsca po przecinku; wartość z większą liczbą miejsc → 400 (bez cichego zaokrąglania).
  - Cena musi być > 0; `0` i wartości ujemne → 400.
  - W API cena to sama kwota (bez pola `currency`) — wiele walut jest poza zakresem v1.
- **[DECYZJA]** Edycja produktu w v1: można zmienić nazwę, opis i cenę. `sku` jest niezmienne. Zmiana ceny nie wpływa na istniejące zamówienia (cena skopiowana w `OrderLine`) — ma to test.
  - Edycja przez `PATCH` z polami `name`, `description`, `price`; pominięte pole pozostaje bez zmian, `"description": null` czyści opis, przesłanie `sku` → 400.
  - Walidacja taka sama jak przy tworzeniu (niepusta nazwa, reguły ceny).
  - Wersjonowanie optymistyczne: produkt ma pole `version` (zwracane w `GET`), które `PATCH` musi odesłać; niezgodna wersja → 409 (Problem Details). Chroni przed nadpisaniem cudzej zmiany (lost update).
- **[DECYZJA]** Brak usuwania produktów w v1.
  - Reguła v1: produkty nie są usuwane fizycznie; powiązania `OrderLine` i `StockMovement` z `Product` nie mają kaskadowego usuwania.
  - Kierunek na v2 (poza zakresem v1): miękkie usunięcie przez status `ACTIVE`/`INACTIVE`. Nieaktywnego produktu nie da się zamówić, domyślnie nie ma go na liście, ale jest dostępny po ID, więc historia zamówień i ruchów pozostaje kompletna; istniejące rezerwacje i ich anulowanie działają dalej.

### 5.2 Inventory
- Przyjęcie towaru: produkt, ilość > 0 (magazyn domyślny — patrz decyzja niżej).
- Odczyt stanu produktu: `onHand`, `reserved`, `available`.
- Każda zmiana stanu zapisuje `StockMovement` (historia).
- **[DECYZJA]** Historia ruchów magazynowych:
  - Typy ruchu: `RECEIPT` (przyjęcie, +`onHand`), `RESERVATION` (+`reserved`), `RELEASE` (−`reserved`, przy anulowaniu).
  - Wpis: typ, produkt, magazyn, ilość (dodatnia), czas, odnośnik do źródła (`stockReceiptId` albo `orderId`).
  - Wpis powstaje w tej samej transakcji co zmiana stanu; rezerwacja zamówienia zapisuje po jednym wpisie na linię.
  - Historia jest tylko do dopisywania — wpisów nie edytujemy ani nie usuwamy.
  - Ponowne (idempotentne) anulowanie nie tworzy drugiego wpisu `RELEASE`.
  - Odczyt w API: `GET /products/{id}/stock-movements` z paginacją.
- **[DECYZJA]** W v1 istnieje jeden magazyn domyślny, tworzony migracją.
  - Model danych zna magazyn: `StockLevel` jest per produkt × magazyn, `StockMovement` wskazuje magazyn.
  - API v1 nie przyjmuje ani nie zwraca magazynu: przyjęcie i rezerwacja dotyczą magazynu domyślnego, odczyt stanu zwraca jeden zestaw `onHand`/`reserved`/`available`.
  - Zamówienie nie wskazuje magazynu. W przyszłości `warehouseId` dojdzie jako pole opcjonalne z wartością domyślną (zmiana zgodna wstecz).

### 5.3 Orders
- Utworzenie zamówienia z co najmniej jedną pozycją (produkt, ilość > 0).
- **[DECYZJA]** Walidacja pozycji zamówienia:
  - Ten sam produkt w więcej niż jednej linii → 400 (bez cichego scalania linii).
  - Linia z nieistniejącym produktem → 404 ze wskazaniem brakującego ID.
  - Produkt istniejący, ale nigdy nieprzyjęty na magazyn (brak stanu), traktujemy jak `available = 0` → 409 brak towaru.
- Rezerwacja **wszystko albo nic**: jeśli którejkolwiek pozycji brakuje, całe zamówienie jest odrzucone i nic nie zostaje zarezerwowane.
- **[DECYZJA]** Idempotencja tworzenia zamówienia (opcjonalny nagłówek `Idempotency-Key`):
  - Klucz jest zapisywany przy zamówieniu, z unikalnym indeksem w bazie; pamiętany bezterminowo.
  - Pierwsze utworzenie → 201. Ponowienie z tym samym kluczem i tą samą treścią → 200 z istniejącym zamówieniem (bez drugiej rezerwacji).
  - Ten sam klucz z inną treścią → 422.
  - Dwa równoczesne żądania z tym samym kluczem: unikalny indeks przepuszcza tylko jedno; drugie wycofuje swoją rezerwację i zwraca istniejące zamówienie (200).
  - Odrzucenie z braku towaru nie zapisuje klucza — ponowienie próbuje zarezerwować jeszcze raz.
  - Bez nagłówka zamówienie tworzy się normalnie, bez ochrony przed duplikatem.
- Cena jednostkowa jest kopiowana do `OrderLine` w chwili zamówienia (późniejsza zmiana ceny nie wpływa na zamówienie).
- Anulowanie zamówienia zwalnia rezerwację.
- Anulowanie jest idempotentne: ponowne anulowanie tego samego zamówienia nie zwalnia towaru drugi raz i nie zwraca błędu.
- Odczyt zamówienia i jego statusu.

**[DECYZJA] Statusy zamówienia w v1:**

```
RESERVED ──cancel──▶ CANCELLED
```

- `RESERVED` jest w v1 stanem końcowym w praktyce: rezerwacja nie wygasa i nie ma kroku wydania towaru. Jedynym przejściem jest anulowanie.
- Konsekwencja: `onHand` obejmuje także towar sprzedany, ale fizycznie niewydany, a `reserved` rośnie do czasu anulowania. `available` pozostaje poprawne. Wydanie towaru (`SHIPPED`) i wygasanie rezerwacji to kandydaci do v2.

**[DECYZJA] Odrzucone zamówienia:** zamówienie odrzucone z braku towaru nie jest zapisywane i nie ma statusu `REJECTED`. Klient dostaje 409 (Problem Details) z listą brakujących pozycji: `sku`, `requested`, `available`. `available` to migawka z chwili odrzucenia i może być już nieaktualna. Statystyki utraconej sprzedaży są poza zakresem v1.

### 5.4 [DECYZJA] Identyfikatory
- Wszystkie encje (`Product`, `Order`, `StockReceipt`, …) mają identyfikator UUIDv7 (nieprzewidywalny, rosnący w czasie, przyjazny indeksom; może być nadany w aplikacji przed zapisem).
- Zasoby w API adresujemy przez `id` (`/products/{id}`, `/orders/{id}`).
- Zamówienie i przyjęcie towaru wskazują produkt przez `productId`, nie `sku`. Klient znający tylko SKU znajduje `id` przez `GET /products?sku=…`.

### 5.5 [DECYZJA] Granice modułów (modularny monolit)
- Jedna aplikacja, trzy moduły jako pakiety najwyższego poziomu: `catalog`, `inventory`, `orders`.
- Każdy moduł wystawia mały publiczny interfejs (np. `CatalogApi`, `InventoryApi`) w pakiecie głównym modułu; encje, repozytoria i implementacja są w podpakietach wewnętrznych i nie są dostępne dla innych modułów.
- Między modułami przekazujemy tylko ID i proste rekordy, nigdy encje. Stan magazynowy zmienia się wyłącznie w module `inventory` — inne moduły tylko przez `InventoryApi` — więc mechanizmu z 6.1 nie da się obejść. (Doprecyzowane 2026-11-01: publiczny interfejs zawiera tylko operacje używane przez inne moduły; szczegóły w `docs/specs/core-v1.md`, „Publiczne interfejsy modułów”.)
- Kierunek zależności: `orders → catalog`, `orders → inventory`, `inventory → catalog`. Bez cykli; `catalog` nie zależy od nikogo.
- Orders wywołuje Catalog i Inventory synchronicznie, w jednej transakcji (rezerwacja i zapis zamówienia — wszystko albo nic).
- Granice pilnuje Spring Modulith: test `ApplicationModules.of(...).verify()` uruchamiany w `./mvnw verify`; złamanie granicy lub cykl → czerwony build.

## 6. Reguły biznesowe (niezmienniki)

1. `reserved ≤ onHand` zawsze, także przy równoczesnych żądaniach.
2. `available` nigdy nie jest ujemne.
3. Ilości są dodatnimi liczbami całkowitymi.
4. Rezerwacja zamówienia jest atomowa (wszystkie pozycje albo żadna).
5. Anulowanie zwalnia dokładnie tyle, ile zostało zarezerwowane, i tylko raz.
6. Spójność z historią: `onHand` = suma `RECEIPT`; `reserved` = suma `RESERVATION` − suma `RELEASE` (dla produktu i magazynu).

### 6.1 [DECYZJA] Mechanizm rezerwacji (jak pilnujemy reguł 1, 2, 4, 5)

Uzasadnienie i odrzucone alternatywy: [ADR 0001](adr/0001-stock-reservation-locking.md).

- Każda pozycja zamówienia rezerwowana jest jednym warunkowym `UPDATE`:
  `UPDATE stock_level SET reserved = reserved + :qty WHERE product_id = :p AND warehouse_id = :w AND on_hand - reserved >= :qty`.
  Zmieniony 1 wiersz → zarezerwowano; 0 wierszy → brak towaru. Sprawdzenie i zapis to jeden krok w bazie, więc nie ma przerwy między odczytem a zapisem, w którą mogłoby wejść inne żądanie. PostgreSQL blokuje zmieniony wiersz do końca transakcji, więc równoczesne rezerwacje tego samego produktu ustawiają się w kolejce i każda widzi świeży stan.
- Wszystkie pozycje zamówienia w jednej transakcji; jeśli którejkolwiek pozycji brakuje → rollback całości (wszystko albo nic). Braki zbieramy ze wszystkich linii (pętla nie przerywa się na pierwszym), aby 409 wymieniał każdą brakującą pozycję.
- Pozycje są przetwarzane w stałej kolejności — posortowane po ID produktu. Zapobiega to deadlockom: dwie transakcje nigdy nie blokują tych samych wierszy w przeciwnej kolejności.
- Anulowanie zmienia status warunkowym `UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND status = 'RESERVED'`. Towar (`reserved = reserved - :qty`, w tej samej kolejności co przy rezerwacji) zwalniany jest w tej samej transakcji i tylko wtedy, gdy ten `UPDATE` zmienił 1 wiersz. Dzięki temu dwa równoczesne anulowania nie zwolnią towaru dwa razy (reguła 5). Odczyt encji i sprawdzenie statusu w Javie przed `save()` tej gwarancji nie daje.
- Transakcje rezerwacji i anulowania działają w izolacji `READ COMMITTED` (domyślnej w PostgreSQL i Springu). Na niej opiera się poprawność: `UPDATE`, który czekał na blokadę wiersza, ponownie sprawdza `WHERE` na świeżej wersji wiersza. Przy `REPEATABLE READ` / `SERIALIZABLE` większość równoczesnych żądań dostaje błąd serializacji (`40001`) i sklep odmawia, choć ma towar. Izolacji nie wolno zmieniać.
- Druga linia obrony w schemacie: `CHECK (reserved >= 0 AND reserved <= on_hand)`.
- Odrzucone alternatywy: optimistic locking (`@Version` + retry) — przy dużej rywalizacji o jeden wiersz prawie każde żądanie dostaje konflikt, wymaga limitu prób i daje fałszywe odrzucenia; `SELECT … FOR UPDATE` — poprawne, ale więcej kodu i ten sam problem kolejności blokad, bez zysku względem warunkowego `UPDATE`.

## 7. Wymagania niefunkcjonalne

- **Współbieżność:** test automatyczny — produkt z `onHand = 5`, 100 równoczesnych zamówień po 1 szt. → dokładnie 5 zamówień `RESERVED`, 95 odrzuconych, `reserved = 5`.
- **API:** prefiks `/api/v1`, JSON, błędy w formacie Problem Details (RFC 9457), dokumentacja OpenAPI.
- **Walidacja:** błędne dane wejściowe → 400 z opisem pól; brak zasobu → 404; duplikat `sku` → 409; brak towaru → 409; niezgodna `version` przy edycji produktu → 409; ten sam `Idempotency-Key` z inną treścią zamówienia → 422.
- **Baza:** PostgreSQL; schemat zmieniany wyłącznie migracjami Flyway.
- **Testy:** testy integracyjne na prawdziwym PostgreSQL (Testcontainers); każda reguła z sekcji 6 ma test.
- **Uruchomienie:** całość startuje według README; CI uruchamia `./mvnw verify` i jest zielone.
- **Stos:** Java 25 LTS, Spring Boot 4.x, Maven, Spring Data JPA / Hibernate, Spring Modulith (weryfikacja granic modułów).

## 8. Poza zakresem v1

Płatności, wysyłka i realizacja zamówień, zapis i statystyki odrzuconych zamówień, wiele magazynów (w tym wybór magazynu do rezerwacji), wygasanie rezerwacji, wydanie towaru (status `SHIPPED`), uwierzytelnianie i role, wiele walut, wiele firm (multi-tenancy), usuwanie produktów, zmiana `sku`, Kafka i zdarzenia, osobne serwisy, frontend, monitoring, AWS/Terraform.

## 9. Kryteria sukcesu v1

- Wszystkie reguły z sekcji 6 są pokryte testami i przechodzą.
- Test współbieżności z sekcji 7 przechodzi stabilnie (wielokrotne uruchomienie).
- Nowa osoba uruchamia projekt i testy, korzystając tylko z README.
- Potrafię wyjaśnić, jaki mechanizm zapobiega overbookingowi i dlaczego go wybrałam.

## 10. Otwarte pytania (do `/grill-with-docs`)

1. ✅ *Rozstrzygnięte — patrz 6.1 i [ADR 0001](adr/0001-stock-reservation-locking.md).* **Mechanizm blokady przy rezerwacji:** warunkowy `UPDATE … WHERE on_hand - reserved >= :qty`, wszystkie pozycje w jednej transakcji, posortowane po ID produktu (brak deadlocków). Optimistic locking odrzucony (konflikty i fałszywe odmowy przy dużej rywalizacji), `SELECT … FOR UPDATE` odrzucony (więcej kodu, bez zysku). Zweryfikowane prototypem (branch `prototype/stock-locking`).
2. ✅ *Rozstrzygnięte — patrz 5.3.* **Odrzucone zamówienia:** nie zapisywać ich wcale czy zapisywać ze statusem `REJECTED` (audyt, statystyki)?
3. ✅ *Rozstrzygnięte — patrz 5.3.* **Idempotencja tworzenia zamówienia:** czy klient API może ponowić `POST` po timeoucie bez ryzyka podwójnego zamówienia (np. nagłówek `Idempotency-Key`)? W v1 czy później?
4. ✅ *Rozstrzygnięte — patrz 5.3.* **Wygasanie rezerwacji:** czy rezerwacja bez dalszego kroku ma kiedyś wygasać? Jeśli w v1 nie ma płatności, czy `RESERVED` to stan końcowy?
5. ✅ *Rozstrzygnięte — patrz 5.2.* **Jeden czy wiele magazynów w v1:** czy jeden magazyn domyślny wystarczy, czy zamówienie ma już teraz wskazywać magazyn?
6. ✅ *Rozstrzygnięte — patrz 5.3.* **Ta sama pozycja dwa razy w zamówieniu:** łączyć linie czy odrzucać żądanie?
7. ✅ *Rozstrzygnięte — patrz 5.5.* **Granice modułów:** czy Orders wywołuje Inventory przez interfejs publiczny modułu (np. `InventoryService`), i jak sprawdzać, że moduły nie sięgają do swoich encji (np. ArchUnit / Spring Modulith teraz czy później)?
8. ✅ *Rozstrzygnięte — patrz 5.4.* **Identyfikatory:** UUID czy sekwencja z bazy? Czy `sku` jest też kluczem w API?
9. ✅ *Rozstrzygnięte — patrz 5.2.* **Historia ruchów magazynowych:** czy rezerwacja i zwolnienie też tworzą `StockMovement`, czy tylko zmiany `onHand`?
10. ✅ *Rozstrzygnięte (kierunek na v2) — patrz 5.1.* **Usunięcie produktu w przyszłości:** jak ma się zachować wobec istniejących zamówień i stanów (soft delete / status nieaktywny)?
