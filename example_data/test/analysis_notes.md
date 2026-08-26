# Synthetic test package for gamma-spectroscopy application

## Warning

Every value in this folder is fabricated for software testing. Do not use these files for physics analysis, publication, calibration, or log ft calculations.

## Package contents

- `levels_122Cd.csv`: 14 fictional levels for a mock daughter nucleus (`150Nd`).
- `transitions_122Ag_122Cd.csv`: 24 fictional but internally linked gamma transitions (`150Pr -> 150Nd`).
- `literature_compare_122Cd.csv`: 18 synthetic comparison rows.
- `122Ag_non_scheme_uncertain_updated.csv`: 45 synthetic non-scheme peaks across five categories.
- `non_scheme_gamma_category_summary_122Ag.csv`: calculated category summary for the synthetic non-scheme rows.
- `non_scheme_gamma_priority_peaks_122Ag.csv`: the three strongest synthetic rows per category.

## Test assumptions

- Level and transition identifiers are internally consistent.
- Every accepted transition connects a higher-energy level to a lower-energy level.
- Gamma energy is calculated as the difference between the two linked level energies.
- Values, labels, counts, uncertainties, literature comparisons, and categories are invented.
