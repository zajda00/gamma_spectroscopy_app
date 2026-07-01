# Analysis notes for 122Ag -> 122Cd gamma spectroscopy input package

## Scope

This file records the current working assumptions and data-processing decisions for the 122Ag -> 122Cd analysis package after the final pre-analysis cleanup of the accepted decay scheme.

## Current accepted input files

- `levels_122Cd.csv`: accepted level table for the current working scheme.
- `transitions_122Ag_122Cd.csv`: accepted scheme transitions only, mapped to `level_initial_id` and `level_final_id`.
- `literature_compare_122Cd.csv`: comparison scaffold restricted to accepted scheme transitions.
- `122Ag_non_scheme_uncertain_updated.csv`: uncertain/non-scheme peaks, including peaks removed from the accepted scheme during cleanup.
- `non_scheme_gamma_category_summary_122Ag.csv`: updated category-level summary for non-scheme peaks.
- `non_scheme_gamma_priority_peaks_122Ag.csv`: strongest non-scheme peaks by category after the cleanup.
- `removed_transitions_from_scheme_122Ag_122Cd.csv`: audit trail for transitions removed from the accepted scheme.
- `removed_levels_122Cd.csv`: audit trail for levels removed from the accepted scheme.

## Parent-state assumptions

- Use only the low-spin 1- and high-spin 9- states in 122Ag.
- Do not include the historical 3+ state in the analysis model.
- Use parent-state-specific half-lives in later log ft calculations.
- Do not update ABF or log ft values at this cleanup stage.

## Current accepted scheme status

The cleaned `transitions_122Ag_122Cd.csv` contains 47 accepted scheme transitions.

The cleaned `levels_122Cd.csv` contains 34 accepted 122Cd levels.

Origin labels in the accepted transition table:

- `known`: 21 transitions
- `2008 work`: 2 transitions
- `new`: 24 transitions

Quality flags in the accepted transition table:

- `normal`: 39 transitions
- `doublet_affected`: 6 transitions
- `estimated_from_doublet_split`: 1 transitions
- `possible_escape_peak`: 1 transitions

## Cleanup decisions applied

The following transitions were removed from the accepted scheme and preserved in `122Ag_non_scheme_uncertain_updated.csv` and `removed_transitions_from_scheme_122Ag_122Cd.csv`.

| E_gamma_keV | level link | integral_counts | relative_percent | decision |
|---:|---|---:|---:|---|
| 622.40 | lv_3267 -> lv_2644 | 2329 | 0.5395 | Removed from scheme; likely possible single-escape/artifact related to 1135.23 keV. |
| 667.00 | lv_3170 -> lv_2502 | 11193 | 2.7335 | Removed from scheme; former 665/667 split retained as non-scheme uncertain, but 3169.71 level not accepted without coincidence support. |
| 867.52 | lv_2196 -> lv_1329 | 373 | 0.1110 | Removed from scheme; very weak/literature-only line not confirmed in current data; 2196.41 level retained via 1627.05 keV. |
| 884.06 | lv_3062 -> lv_2178 | 134 | 0.0404 | Removed from scheme; 3061.70 level not accepted because 884 keV line is too weak/not confirmed. |

The following levels were removed from the accepted level table and preserved in `removed_levels_122Cd.csv`.

| level_id | E_level_keV | Jpi | decision |
|---|---:|---|---|
| lv_3267 | 3266.52 |  | Removed with 622.40 keV transition; likely possible escape/artifact, no longer accepted in main scheme. |
| lv_3170 | 3169.71 |  | Removed with 667.00 keV transition; lacks convincing coincidence support in current data. |
| lv_3062 | 3061.7 | (8+) | Removed with 884.06 keV transition; line too weak/not confirmed in current data. |

Important: level `lv_2196` remains accepted because the 2196.41 keV level is still supported by the 1627.05 keV transition to 569.36 keV. Only the weak 867.52 keV branch from this level was removed.

## Reasoning for individual cleanup decisions

### 622.40 keV and level 3266.52 keV

The 622.40 keV line was removed from the accepted scheme because it is flagged as a possible escape/artifact candidate, specifically as a possible single-escape feature related to the 1135.23 keV line. The associated level 3266.52 keV is not retained in the accepted level list.

### 667.00 keV and level 3169.71 keV

The 667.00 keV line was removed from the accepted scheme even though a provisional doublet split had previously assigned nonzero intensity to it. The current decision is that the 3169.71 keV level lacks convincing coincidence support, so the 667.00 keV row is preserved only as an uncertain removed-from-scheme peak.

### 884.06 keV and level 3061.70 keV

The 884.06 keV line was removed from the accepted scheme because it is extremely weak and not convincingly confirmed in the current data. Since it was the only accepted support for level 3061.70 keV in the working scheme, that level was also removed from the accepted level table.

### 867.52 keV

The 867.52 keV line was removed from the accepted scheme because it is very weak and not confirmed in the current data. The 2196.41 keV level remains accepted due to the 1627.05 keV branch.

## Dublet decisions retained in the accepted scheme

### 465.50 / 466.36 keV

The unresolved 465/466 keV structure remains split using the previous gated-spectrum estimate.

- 465.50 keV: integral = 10698 counts, relative intensity = 1.9911 percent
- 466.36 keV: integral = 25473 counts, relative intensity = 4.7478 percent

### 665.27 / 667.00 keV

Only the 665.27 keV branch remains in the accepted scheme.

- 665.27 keV: integral = 26385 counts, relative intensity = 6.4309 percent
- 667.00 keV: removed from accepted scheme and preserved as uncertain/non-scheme.

## Non-scheme gamma categories after cleanup

| category | n_rows | integral_counts_sum | relative_percent_sum | absolute_percent_sum |
|---|---:|---:|---:|---:|
| background | 4 | 12025.0 | 2.5984 | 2.3183 |
| daughters | 18 | 83716.0 | 33.671 | 30.0423 |
| neglected | 36 | 17533.0 | 4.4458 | 3.9665 |
| uncertain | 50 | 71508.0 | 24.4749 | 21.8372 |
| unidentified | 59 | 51099.0 | 15.7589 | 14.061 |

Interpretation:

- `daughters` and `background` remain outside the 122Ag -> 122Cd feeding balance.
- `uncertain` now includes the four peaks removed from the accepted scheme: 622.40, 667.00, 867.52 and 884.06 keV.
- `unidentified` remains a separate reservoir of unplaced strength.
- `neglected` remains low-priority but useful for residual-intensity bookkeeping.

## Ground-state feeding strategy

Do not hard-code zero ground-state feeding as the final result. The current strategy remains:

1. Start with zero ground-state feeding as an initial normalization assumption.
2. Compute apparent beta feeding for higher-lying 0+ levels.
3. Use the behavior of higher 0+ feeding to estimate possible nonzero feeding to the 122Cd ground state.
4. Recompute the intensity balance and log ft values with the estimated ground-state feeding.
5. Iterate until the result is stable enough for reporting.

## Immediate next checks

1. Use the cleaned accepted scheme for the next beta-feeding and log ft calculation pass.
2. Keep removed lines out of the main balance unless later bin-by-bin coincidence data justify reinstating them.
3. Treat the updated uncertain list as the audit trail for removed but preserved spectral information.
4. Rebuild literature comparison only against the accepted scheme transitions.
