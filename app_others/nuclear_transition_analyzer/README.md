# Nuclear Transition Analyzer

Narzędzie do praktycznej analizy przejść gamma w danych jądrowych: multipolowości, typów przejść, reguł wyboru, spinów i stosunków prawdopodobieństw w przybliżeniu Weisskopfa.

Projekt jest przygotowany pod dane CSV podobne do `AI_processing_2_v2.csv`, gdzie intensywność w kolumnie `relative (%)` oznacza intensywność samej gamma, bez poprawki na konwersję wewnętrzną.

## Najważniejsze funkcje

- Wczytywanie CSV z energiami gamma, poziomami początkowymi i końcowymi, spinami oraz intensywnościami gamma.
- Automatyczne rozpoznawanie kolumn takich jak:
  - `E (keV)`
  - `from (keV)`
  - `from spin`
  - `onto (keV)`
  - `onto spin`
  - `relative (%)`
  - opcjonalne kolumny ICC: `E1`, `M1`, `E2`, `M2`, ...
- Porównanie dwóch wpisanych energii gamma.
- Obliczenie stosunku intensywności gamma.
- Porównanie ze stosunkami przybliżonych szybkości Weisskopfa.
- Jawne rozdzielenie tego, co jest pewne, sugerowane i niewyznaczalne.
- Twarde reguły wyboru są stosowane tylko wtedy, gdy spiny są pewne.
- Spiny w nawiasach, np. `(4+)`, `((3+))`, `(3,4+),(5-)`, są traktowane wyłącznie jako sugestie.
- Wyniki są prezentowane w sposób kompaktowy, bez technicznych parametrów pomocniczych.

## Instalacja

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Uruchomienie aplikacji graficznej

```bash
streamlit run app.py
```

Aplikacja otworzy się w przeglądarce. Możesz wczytać własny CSV albo użyć przykładowego pliku z katalogu `examples/`.

## Użycie z terminala

Porównanie dwóch przejść:

```bash
python -m nuclear_transition_analyzer.cli examples/AI_processing_2_v2.csv --energy 650.05 848.72
```

Skan tabeli:

```bash
python -m nuclear_transition_analyzer.cli examples/AI_processing_2_v2.csv --scan
```

## Jak interpretować wyniki

Aplikacja porównuje:

```text
I_gamma(E1) / I_gamma(E2)
```

ze stosunkiem przybliżonych szybkości Weisskopfa:

```text
lambda_W.u.(multipole_1, E1) / lambda_W.u.(multipole_2, E2)
```

To jest test rzędu wielkości, nie precyzyjne wyznaczenie multipolowości. Jeżeli różnica mieści się w okolicach jednego rzędu wielkości, wariant należy traktować jako fizycznie możliwy. Nie oznacza to, że jest udowodniony.

## Pewne spiny kontra sugestie

Aplikacja stosuje twarde ograniczenia tylko do spinów bez nawiasów:

| Zapis w CSV | Interpretacja |
|---|---|
| `2+` | pewny spin i parzystość |
| `(4+)` | sugestia |
| `((3+))` | sugestia |
| `(3,4+),(5-)` | zestaw sugestii |
| puste pole | brak ograniczenia |

Dzięki temu program nie odrzuca wariantów tylko dlatego, że starszy schemat podaje spin w nawiasie.

## Reguły fizyczne zaimplementowane w wersji 0.1

- Warunek momentu pędu: `|Ji - Jf| <= L <= Ji + Jf`.
- Zakaz przejścia gamma `0 -> 0`.
- Przejścia elektryczne: zmiana parzystości zgodna z `(-1)^L`.
- Przejścia magnetyczne: zmiana parzystości zgodna z `(-1)^(L+1)`.
- Preferencja dla niższych multipolowości w rankingu, bez sztucznego wykluczania wyższych multipolowości, jeżeli dane są niepełne.
- Konwersja wewnętrzna nie jest domyślnie używana do zmiany intensywności, bo wejściowa intensywność to sama gamma. Jeśli kolumny ICC są obecne i współczynnik jest duży, aplikacja dodaje ostrzeżenie.

## Ograniczenia

- Weisskopf jest dużym przybliżeniem i powinien być używany jako filtr rzędu wielkości.
- Program nie zastępuje analizy koincydencji gamma-gamma, rozkładów kątowych, polaryzacji ani bezpośrednich pomiarów konwersji wewnętrznej.
- Dla spinów w nawiasach program pokazuje zgodność lub konflikt z sugestią, ale nie używa ich jako twardego dowodu.

## Plan rozbudowy

Kolejne sensowne moduły:

1. Parser schematów rozpadu w PostScripcie.
2. Automatyczna weryfikacja całego schematu względem CSV.
3. Integracja z BrIcc, lokalna lub przez przygotowane tabele współczynników konwersji.
4. Integracja z kalkulatorem logft.
5. Raport HTML/PDF z listą przejść pewnych, możliwych, spekulatywnych i sprzecznych.

## Testy

```bash
pytest
```
