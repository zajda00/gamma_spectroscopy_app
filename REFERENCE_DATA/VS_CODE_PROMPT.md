# Prompt for the Visual Studio assistant

Adapt the gamma spectroscopy application to the new project data model.

## Project locations

Use these placeholders, not hard-coded paths:

- `PROJECT_ROOT = <ROOT_FOLDER_OF_THE_APPLICATION>`
- `REFERENCE_CSV_FOLDER = <FOLDER_WITH_THE_REFERENCE_CSV_FILES>`
- `INPUT_WORKBOOK = <PATH_TO_THE_REFERENCE_ODS_OR_XLSX>`

The reference CSV folder contains CSVs already produced after the current ODS/XLSX import step. It is a reference fixture for development and tests, not a second mandatory input channel.

## Required user workflow

1. Opening a project loads exactly one `.ods` or `.xlsx` workbook.
2. The workbook contains only calibrated input data and measurement metadata, not a precomputed decay scheme.
3. The application materializes an editable project folder with `input/`, `working/`, `auxiliary/`, `results/` and `reports/` subdirectories.
4. The user edits levels and transition assignments with a live scheme preview.
5. ABF is calculated only after clicking a dedicated `Calculate apparent beta feeding` button.
6. Logft is calculated only after clicking a dedicated `Calculate logft` button.
7. Changing a level, transition, spin-parity assignment, normalization or ICC value invalidates dependent results and requires an explicit recalculation.
8. The user can import an existing analysis backwards by placing already prepared working CSVs in the project folder. This is an optional compatibility path, not the normal raw-data workflow.

## New workbook sheets

The importer must recognise these sheets:

### `raw_peaks`

Required columns:

```text
peak_id
e_gamma_keV
de_gamma_keV
fwhm_keV
fwhm_err_percent
integral_counts
integral_err
efficiency
efficiency_err
fit_error_percent
```

These are the minimum calibrated peak measurements. Do not expect level IDs, transition IDs, Jπ, ABF, logft, multipolarity or ICC values in this sheet.

### `coincidences`

Required columns:

```text
coincidence_id
peak_id_a
peak_id_b
gate_energy_keV
gate_width_keV
coincidence_strength
dcoincidence_strength
relationship_status
comment
```

The sheet may be empty, but the schema must remain valid. Validate that every referenced peak ID exists in `raw_peaks`.

### `normalization`

Required columns:

```text
normalization_id
reference_peak_id
relative_reference_percent
absolute_reference_percent
dabsolute_reference_percent
normalization_status
include_internal_conversion
comment
```

Validate that `reference_peak_id` exists in `raw_peaks`.

## Working files

Create or load these files after import:

```text
working/levels_122Cd.csv
working/transitions_122Ag_122Cd.csv
working/spin_hypotheses_122Cd.csv
working/non_scheme_uncertain_122Ag.csv
working/abf_overrides_122Ag_122Cd.csv
```

### Levels

Use:

```text
level_id
E_level_keV
dE_level_keV
Jpi
Jpi_status
Jpi_origin_year
T12_s
dT12_s
level_status
comment
```

`level_id` must be stable. Never identify a level only by rounded energy.

### Transitions

Use:

```text
transition_id
peak_id
level_initial_id
level_final_id
assignment_status
in_scheme
quality_flag
multipolarity
mixing_ratio
dmixing_ratio
comment
```

Do not duplicate `e_gamma_keV`, counts, efficiency or corrected intensities here. Resolve those values through `peak_id`.

### Spin hypotheses

Use:

```text
hypothesis_id
level_id
Jpi
status
confidence
origin_year
basis
comment
```

Changing the active hypothesis must invalidate ABF and logft results, because selection rules and parent-state matching may change.

## Derived calculations

Do not calculate ABF or logft during workbook loading.

### ABF button

The button must call the ABF calculation only after the user requests it. Use the active `working/transitions` and `working/levels` state, resolve peak intensities from `raw_peaks`, apply efficiency correction and normalization, then write:

```text
results/abf_results_122Ag_122Cd.csv
```

Suggested columns:

```text
run_id
level_id
E_level_keV
incoming_percent
outgoing_percent
abf_raw_percent
abf_used_percent
status
comment
calculated_at
```

Negative ABF values must remain visible as `abf_raw_percent`. Do not silently replace them with zero. A separate `abf_used_percent` may contain the chosen clipped or overridden value, with a clear status.

### Logft button

This button must run only after ABF exists or the user explicitly enables a manual ABF override. It must use:

```text
Q_beta
parent excitation energy
level energy
parent half-life
ABF branch percentage
parent Jπ
daughter Jπ
```

Write:

```text
results/logft_results_122Ag_122Cd.csv
```

Do not print one warning for every branch. Store branch-specific failures in the result table and show a summary in the GUI.

Each result must include `backend` with one of:

```text
radiation_report
local_approximation
not_calculated
error
```

## ICC integration

Use:

```text
auxiliary/icc_122Ag_122Cd.csv
```

Required columns:

```text
transition_id
peak_id
E_gamma_keV
multipolarity
mixing_ratio
dmixing_ratio
alpha_tot
dalpha_tot
icc_status
source
comment
```

For now, provide editable manual cells. Later add a BrIcc backend that fills the same table. Do not add dozens of columns such as `icc_E1`, `icc_M1`, `icc_E2` to the main transitions table. Multiple ICC candidates should be represented as rows or as a separate calculation run table.

## Compatibility requirements

1. Keep support for the current legacy filenames during migration:
   - `levels.csv`
   - `transitions.csv`
   - `non_scheme_uncertain.csv`
   - `literature_compare.csv`
   - `non_scheme_gamma_priority_peaks.csv`
   - `non_scheme_category_summary.csv`
2. Legacy `transitions.csv` contains duplicated raw peak fields. On import, split it into `raw_peaks` and `working/transitions`, deduplicating by `peak_id`.
3. Merge legacy `non_scheme_uncertain.csv` into `raw_peaks` and create an editable `non_scheme_uncertain_122Ag.csv` view containing only `peak_id`, energy, status, category, priority and comment.
4. Treat priority and category summary files as generated reports, not authoritative input.
5. Ignore internal file identifiers and remove fields such as `file_tool_id` from user-facing data.
6. Keep human-readable provenance in `source` or `comment`, never internal IDs.

## UI changes

Add two separate buttons:

```text
Calculate apparent beta feeding
Calculate logft
```

The buttons must not be triggered automatically by NNDC import.

After editing a level or transition, show:

```text
ABF: stale
logft: stale
```

After recalculation, show the backend and run timestamp next to the values on the live scheme.

## Tests

Add tests for:

1. Loading one ODS and one XLSX with the same sheets.
2. Loading `raw_peaks` without any scheme assignments.
3. Creating, editing and deleting a level without modifying raw peak data.
4. Creating, editing and deleting a transition by changing only its IDs and assignment fields.
5. ABF not running during import.
6. Logft not running during import.
7. Recalculation after a spin-parity change.
8. Validation of missing peak and level references.
9. Preservation of negative raw ABF values.
10. Manual ICC values surviving a project reload.
11. Legacy CSV migration into the new working format.
12. RadiationReport failures being stored per branch rather than printed repeatedly.

Do not rewrite the scientific formulas unless a test demonstrates a specific error. First implement the data separation, explicit button actions, invalidation flags, compatibility loader and tests.
