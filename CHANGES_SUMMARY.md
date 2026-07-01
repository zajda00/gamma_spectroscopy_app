# Podsumowanie zmian - Pierwsza większa poprawka do app_decay_scheme

## Pliki zmodyfikowane

### 1. `app_decay_scheme/main_window.py` - główne zmiany

#### 1.1. Import:
- **Dodano**: `from datetime import datetime` - do budowania timestampów w ścieżkach output

#### 1.2. Nowe metody helperów w klasie MainWindow:

**`_try_auto_load_project()`** (linia ~30)
- Automatycznie ładuje projekt z folderu `data/` na startup
- Jeśli folder nie istnieje, metoda po cichu zwraca bez błędu
- Jeśli ładowanie się nie powiedzie, wypisuje błąd w konsolę ale nie crashuje
- Wywoływana w `__init__`

**`_try_auto_load_template()`** (linia ~40)
- Automatycznie ładuje template z `templates/scheme_template.eps` na startup
- Jeśli plik nie istnieje, metoda po cichu zwraca bez błędu
- Wywoływana w `__init__`

**`_build_output_path_if_needed()`** (linia ~48)
- Helper do inicjalizacji output path
- Wywołuje `_build_output_path()` jeśli output_eps_path jest None
- Wywoływana w `__init__`

**`_build_output_path()`** (linia ~53)
- Buduje automatyczną ścieżkę wyjściową: `outputs/parent_daughter_timestamp.eps`
- Timestamp w formacie: `YYYY-MM-DD_HH-MM-SS` (dokładnie rok-miesiąc-dzień_godzina-minuta-sekunda)
- Przykład: `122Ag_122Cd_2026-07-01_03-15-42.eps`
- Automatycznie tworzy folder `outputs/` jeśli nie istnieje
- Używana w `reload_scheme()` i `maybe_write_scheme()`

#### 1.3. Modyfikacja `__init__`:
- Dodano 3 linie na koniec funkcji:
  ```python
  # Auto-load data folder and template at startup
  self._try_auto_load_project()
  self._try_auto_load_template()
  self._build_output_path_if_needed()
  ```

#### 1.4. GUI - dodano przycisk "Reload Scheme":
- W `_setup_ui()` dodano: `self.reload_btn = QPushButton('Reload Scheme')`
- Przycisk powiązany z `reload_btn.clicked.connect(self.reload_scheme)`
- Umieszczony między przyciskami "Select output" i "Recompute ABF"

#### 1.5. Nowa metoda `reload_scheme()` (linia ~165):
- Ręczny trigger do odświeżenia schematu
- Sprawdza czy projekt i template są załadowane
- Automatycznie buduje output path jeśli nie istnieje
- Wymusza przerabianie `recompute_everything()` + `maybe_write_scheme(force=True)`
- Pokazuje komunikat jeśli brakuje projektu lub template

#### 1.6. Zmiana `_sync_settings_to_model()` (linia ~180):
- **Usunięto**: automatyczne wywoływanie `self.maybe_write_scheme()`
- **Powód**: Zapobieganie niezamierzonej regeneracji schematu podczas edycji Settings
- **Nowe zachowanie**: Settings są synchronizowane z modelem, ale schemat regeneruje się dopiero po kliknięciu "Reload Scheme"
- Dodawana komentarz w kodzie wyjaśniający zmianę

#### 1.7. Zmiana `select_output()` (linia ~195):
- Dodano fallback: jeśli użytkownik anuluje dialog, generuje automatyczną ścieżkę
- `elif self.output_eps_path is None: self.output_eps_path = self._build_output_path()`

#### 1.8. Zmiana `maybe_write_scheme()` (linia ~210):
- **Usunięto** warunek: `or self.output_eps_path is None` (wymaganie outputu)
- **Dodano** automatyczne tworzenie output path:
  ```python
  if self.output_eps_path is None:
      self.output_eps_path = self._build_output_path()
  ```
- **Efekt**: Schemat może być regenerowany nawet bez ręcznego wybrania output pliku

---

## Pliki o created / testowe

### 2. `test_autoload.py` - test automation (nowy plik)
- Smoke test dla logiki auto-load
- Testuje: auto-load projektu, auto-load template, budowanie output path
- **Wszystkie testy przechodzą** ✅

### 3. Foldery przygotowane do testów (utworzone automatycznie):
```
data/                           (folder dla auto-load projektu)
  ├── beta_inputs_122Ag_122Cd.md
  ├── levels_122Cd.csv
  ├── transitions_122Ag_122Cd.csv
  └── analysis_notes.md

templates/                      (folder dla auto-load template)
  └── scheme_template.eps

outputs/                        (folder dla wygenerowanych EPS/PNG)
  └── [wygenerowane dynamicznie]
```

---

## Problemy rozwiązane

### ✅ 1. Auto-load projektu z `data/` folderu
- Na startup aplikacja próbuje załadować projekt automatycznie
- Jeśli się powiedzie, user widzi załadowane dane w UI od razu
- Jeśli się nie powiedzie (brak folderu, złe pliki), aplikacja działa dalej bez erroru

### ✅ 2. Auto-load template z `templates/scheme_template.eps`
- Na startup aplikacja szuka template w `templates/scheme_template.eps`
- Jeśli istnieje, ustawia go automatycznie
- Jeśli nie istnieje, aplikacja działa dalej bez erroru

### ✅ 3. Automatyczne tworzenie output path
- Output EPS/PNG Jest tworzony w `outputs/` folderze
- Nazwa: `parent_daughter_timestamp.eps` (np. `122Ag_122Cd_2026-07-01_03-15-42.eps`)
- Folder `outputs/` jest tworzony automatycznie jeśli nie istnieje
- User nie musi ręcznie klikać "Select output"

### ✅ 4. Problem z odświeżaniem Settings
- **Diagnoza**: Problem polegał na tym, że każda zmiana w Settings => natychmiastowa regeneracja schematu
- **Rozwiązanie**: Usunięto automatyczne `maybe_write_scheme()` z `_sync_settings_to_model()`
- **Efekt**: Zmiany w Settings (parent, daughter, Q_beta, etc.) nie powodują już niezamierzonej regeneracji
- **Wie użytkownika**: Po zmianach w Settings, user kliknie "Reload Scheme" aby odświeżyć EPS

### ✅ 5. Nowy przycisk "Reload Scheme"
- Wyraźnie widoczny w GUI (między "Select output" i "Recompute")
- Zwraca pełną regenerację: recompute ABF/logft + regeneracja EPS + regeneracja PNG
- Pracuje niezależnie od checkboxa "Auto write EPS"

---

## Jak przetestować nowe zachowanie

### Test 1: Auto-load na startup
```
1. Upewnij się, że foldery zawierają poprawne pliki:
   ✓ data/beta_inputs_122Ag_122Cd.md
   ✓ data/levels_122Cd.csv
   ✓ data/transitions_122Ag_122Cd.csv
   ✓ templates/scheme_template.eps

2. Uruchom aplikację: python main.py
   Oczekiwany rezultat:
   - Projekt zostaje załadowany automatycznie
   - Tabele Levels i Transitions wypełniają się danymi
   - Settings wypełniają się danymi z beta_inputs
```

### Test 2: Settings bez auto-regeneracji
```
1. Aplikacja uruchomiona, projekt załadowany
2. Zmień w Settings np. "Parent nucleus" z "122Ag" na "126Ag"
3. Obserwuj: EPS scheme NIE powinien się zmieniać (!)
4. Kliknij "Reload Scheme"
5. Obserwuj: Schemat regeneruje się z nowymi parent/daughter
```

### Test 3: Auto-output path
```
1. Nie klikaj "Select output EPS/TXT"
2. Kliknij "Reload Scheme" lub zmień dane w Levels/Transitions
3. Obserwuj: W konsoli/log powinno być widać że schemat został wygenerowany
4. Sprawdź folder `outputs/`:
   Oczekiwany plik: 122Ag_122Cd_2026-07-01_XX-XX-XX.eps
```

### Test 4: Robustness bez folderu `data`
```
1. Usuń (rename) folder `data/`
2. Uruchom aplikację
3. Oczekiwany rezultat: Aplikacja startuje bez erroru
4. Manual load: Kliknij "Load project folder" i zaznacz inny folder
```

### Test 5: Robustness bez template
```
1. Usuń (rename) plik `templates/scheme_template.eps`
2. Uruchom aplikację
3. Oczekiwany rezultat: Projekt się ładuje, ale "Reload Scheme" pokaże warning
4. Manual load: Kliknij "Select scheme template"
```

---

## Jakie problemy NIE są jeszcze rozwiązane

- ❌ Hardcoded nuklidy (122Ag, 122Cd) w `loaders.py` i `eps_template.py` - wymaga osobnego refactora
- ❌ Weisskopf adapter wciąż jako placeholder
- ❌ Brak undo/redo
- ❌ Edycja tabel z precyzją float→str→float
- ❌ Kompletny test suite (testy unit dla compute functions)

---

## Notatki techniczne

### Stabilność
- **Brak crashu** gdy brakuje folderu `data`, `templates`, `outputs`
- **Bezpieczne fallbacks** we wszystkich auto-load metodach
- **Print statements** (nie exceptions) dla diagnostyki

### Kompatybilność z istniejącym kodem
- Nie zmieniono fizyki obliczeń ABF i logft
- Nie zmieniono struktury modelu (models.py)
- Nie zmieniono interfejsu loaders.py
- Kompatybilne z istniejącymi plikami wejściowymi

### Ergonomia
- 2 nowe helpery do automatyzacji (auto-load, auto-output)
- 1 nowy przycisk (Reload Scheme) do ręcznej kontroli
- Mniej "magii" - user ma pełną kontrolę kiedy regenerować schemat

---

## Changelog

**Wersja 0.1.1** (po zmianach):
- ✨ Auto-load projektu z `data/` folderu
- ✨ Auto-load template z `templates/scheme_template.eps`
- ✨ Auto-generated output path: `outputs/parent_daughter_timestamp.eps`
- ✨ Nowy "Reload Scheme" button
- 🐛 Fix: Zapobieganie niezamierzonej regeneracji schematu z Settings
- 📚 Dodano `test_autoload.py` do smoke testingu

---

