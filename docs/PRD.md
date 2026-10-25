# FulfillFlow Core (v1) — Product Requirements Document

> Status: **szkic do sesji `/grill-with-docs`**.
> Punkty oznaczone **[ZAŁOŻENIE]** to robocze decyzje do potwierdzenia lub zmiany. Sekcja „Otwarte pytania” to materiał, który agent ma ze mną przegadać przed napisaniem specyfikacji.

## 1. Problem

Mała firma sprzedająca online prowadzi stany magazynowe w arkuszu. Przy kilku zamówieniach składanych jednocześnie zdarza się sprzedaż towaru, którego już nie ma (overselling), a anulowane zamówienia nie zwalniają towaru. Firma potrzebuje backendu, który jest jedynym źródłem prawdy o produktach, stanach i zamówieniach oraz gwarantuje, że nie sprzeda więcej, niż ma.

## 2. Cel v1

Działające REST API dla trzech obszarów: **Catalog**, **Inventory**, **Orders**, w którym:
- produkty można tworzyć i przeglądać,
- towar można przyjmować do magazynu i sprawdzać jego stan,
- zamówienie rezerwuje towar albo zostaje odrzucone,
- równoczesne zamówienia nigdy nie rezerwują więcej towaru, niż jest dostępne,
- anulowanie zamówienia zwalnia rezerwację.

## 3. Użytkownicy (aktorzy)

W v1 nie ma uwierzytelniania; aktorzy są pojęciowi i pomagają opisać przypadki użycia.

| Aktor | Co robi |
|---|---|
| Operator katalogu | Dodaje i przegląda produkty |
| Magazynier | Przyjmuje towar, sprawdza stany |
| System sprzedażowy (klient API) | Składa i anuluje zamówienia w imieniu kupujących |

## 4. Słownik domeny (ubiquitous language)

Nazwy w kodzie będą po angielsku; ten słownik jest wiążący dla nazewnictwa.

| Termin (kod) | Znaczenie |
|---|---|
| `Product` | Pozycja w katalogu, identyfikowana unikalnym `sku` |
| `Sku` | Unikalny kod produktu nadawany przez operatora |
| `Warehouse` | Fizyczny magazyn |
| `StockLevel` | Stan produktu w magazynie: `onHand`, `reserved` |
| `onHand` | Liczba sztuk fizycznie w magazynie |
| `reserved` | Liczba sztuk zarezerwowanych przez aktywne zamówienia |
| `available` | `onHand − reserved`; tylko to można zarezerwować |
| `StockReceipt` | Przyjęcie towaru do magazynu (zwiększa `onHand`) |
| `StockMovement` | Wpis w historii zmian stanu (przyjęcie, rezerwacja, zwolnienie) |
| `Order` | Zamówienie złożone przez system sprzedażowy |
| `OrderLine` | Pozycja zamówienia: produkt, ilość, cena jednostkowa w chwili zamówienia |
| `Reservation` | Zablokowanie dostępnych sztuk na potrzeby zamówienia |

## 5. Zakres funkcjonalny v1

### 5.1 Catalog
- Utworzenie produktu: `sku`, nazwa, opcjonalny opis, cena.
- `sku` jest unikalne; próba utworzenia duplikatu kończy się błędem.
- Lista produktów z paginacją.
- Pobranie produktu po identyfikatorze.
- **[ZAŁOŻENIE]** Cena w jednej walucie (PLN), przechowywana jako kwota dziesiętna, nie `double`.
- **[ZAŁOŻENIE]** Brak edycji i usuwania produktów w v1.

### 5.2 Inventory
- Przyjęcie towaru: produkt, magazyn, ilość > 0.
- Odczyt stanu produktu: `onHand`, `reserved`, `available`.
- Każda zmiana stanu zapisuje `StockMovement` (historia).
- **[ZAŁOŻENIE]** W v1 istnieje jeden magazyn domyślny, tworzony migracją. Model danych nie blokuje dodania kolejnych magazynów później.

### 5.3 Orders
- Utworzenie zamówienia z co najmniej jedną pozycją (produkt, ilość > 0).
- Rezerwacja **wszystko albo nic**: jeśli którejkolwiek pozycji brakuje, całe zamówienie jest odrzucone i nic nie zostaje zarezerwowane.
- Cena jednostkowa jest kopiowana do `OrderLine` w chwili zamówienia (późniejsza zmiana ceny nie wpływa na zamówienie).
- Anulowanie zamówienia zwalnia rezerwację.
- Anulowanie jest idempotentne: ponowne anulowanie tego samego zamówienia nie zwalnia towaru drugi raz i nie zwraca błędu.
- Odczyt zamówienia i jego statusu.

**[ZAŁOŻENIE] Statusy zamówienia w v1:**

```
RESERVED ──cancel──▶ CANCELLED
```

Zamówienie odrzucone z braku towaru nie jest zapisywane (zwracany jest błąd).

## 6. Reguły biznesowe (niezmienniki)

1. `reserved ≤ onHand` zawsze, także przy równoczesnych żądaniach.
2. `available` nigdy nie jest ujemne.
3. Ilości są dodatnimi liczbami całkowitymi.
4. Rezerwacja zamówienia jest atomowa (wszystkie pozycje albo żadna).
5. Anulowanie zwalnia dokładnie tyle, ile zostało zarezerwowane, i tylko raz.

## 7. Wymagania niefunkcjonalne

- **Współbieżność:** test automatyczny — produkt z `onHand = 5`, 100 równoczesnych zamówień po 1 szt. → dokładnie 5 zamówień `RESERVED`, 95 odrzuconych, `reserved = 5`.
- **API:** prefiks `/api/v1`, JSON, błędy w formacie Problem Details (RFC 9457), dokumentacja OpenAPI.
- **Walidacja:** błędne dane wejściowe → 400 z opisem pól; brak zasobu → 404; duplikat `sku` → 409; brak towaru → 409.
- **Baza:** PostgreSQL; schemat zmieniany wyłącznie migracjami Flyway.
- **Testy:** testy integracyjne na prawdziwym PostgreSQL (Testcontainers); każda reguła z sekcji 6 ma test.
- **Uruchomienie:** całość startuje według README; CI uruchamia `./mvnw verify` i jest zielone.
- **Stos:** Java 25 LTS, Spring Boot 4.x, Maven, Spring Data JPA / Hibernate.

## 8. Poza zakresem v1

Płatności, wysyłka i realizacja zamówień, wygasanie rezerwacji, uwierzytelnianie i role, wiele walut, wiele firm (multi-tenancy), edycja/usuwanie produktów, Kafka i zdarzenia, osobne serwisy, frontend, monitoring, AWS/Terraform.

## 9. Kryteria sukcesu v1

- Wszystkie reguły z sekcji 6 są pokryte testami i przechodzą.
- Test współbieżności z sekcji 7 przechodzi stabilnie (wielokrotne uruchomienie).
- Nowa osoba uruchamia projekt i testy, korzystając tylko z README.
- Potrafię wyjaśnić, jaki mechanizm zapobiega overbookingowi i dlaczego go wybrałam.

## 10. Otwarte pytania (do `/grill-with-docs`)

1. **Mechanizm blokady przy rezerwacji:** optimistic locking (`@Version` + retry), pessimistic locking (`SELECT … FOR UPDATE`) czy atomowy `UPDATE … WHERE onHand - reserved >= :qty`? Jakie są kompromisy przy wielopozycyjnych zamówieniach (kolejność blokad, deadlocki)?
2. **Odrzucone zamówienia:** nie zapisywać ich wcale czy zapisywać ze statusem `REJECTED` (audyt, statystyki)?
3. **Idempotencja tworzenia zamówienia:** czy klient API może ponowić `POST` po timeoucie bez ryzyka podwójnego zamówienia (np. nagłówek `Idempotency-Key`)? W v1 czy później?
4. **Wygasanie rezerwacji:** czy rezerwacja bez dalszego kroku ma kiedyś wygasać? Jeśli w v1 nie ma płatności, czy `RESERVED` to stan końcowy?
5. **Jeden czy wiele magazynów w v1:** czy jeden magazyn domyślny wystarczy, czy zamówienie ma już teraz wskazywać magazyn?
6. **Ta sama pozycja dwa razy w zamówieniu:** łączyć linie czy odrzucać żądanie?
7. **Granice modułów:** czy Orders wywołuje Inventory przez interfejs publiczny modułu (np. `InventoryService`), i jak sprawdzać, że moduły nie sięgają do swoich encji (np. ArchUnit / Spring Modulith teraz czy później)?
8. **Identyfikatory:** UUID czy sekwencja z bazy? Czy `sku` jest też kluczem w API?
9. **Historia ruchów magazynowych:** czy rezerwacja i zwolnienie też tworzą `StockMovement`, czy tylko zmiany `onHand`?
10. **Usunięcie produktu w przyszłości:** jak ma się zachować wobec istniejących zamówień i stanów (soft delete / status nieaktywny)?
