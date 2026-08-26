# 122Ag project data layout

This package separates the single-file batch input from editable project state and generated results.

## Input, suitable for one ODS/XLSX workbook

- `raw_gamma_peaks_122Ag.csv`: calibrated peak energy, peak width, counts and efficiency. No level or transition assignments.
- `raw_coincidences_122Ag.csv`: coincidence relations. Empty schema here because no coincidence table was supplied in the current package.
- `intensity_normalization_122Ag.csv`: measurement normalization metadata required to convert corrected peak yields into absolute intensities.

The workbook `122Ag_input_template.ods` contains these three sheets.

## Working files created by the application

- `levels_122Cd.csv`: current editable daughter levels.
- `transitions_122Ag_122Cd.csv`: current editable scheme assignments, referring to `peak_id` and level IDs.
- `spin_hypotheses_122Cd.csv`: alternative or proposed Jπ assignments.
- `non_scheme_uncertain_122Ag.csv`: editable classification view referring back to raw peaks.
- `abf_overrides_122Ag_122Cd.csv`: optional manual ABF overrides.

## Auxiliary files

- `icc_122Ag_122Cd.csv`: manually maintained ICC values now, later replaceable by BrIcc output.
- `literature_compare_122Cd.csv`: cleaned literature comparison table.

## Generated reports and results

`reports/` and `results/` are outputs. They should be regenerated after changing levels, transitions, spin hypotheses, normalization or ICC values.

The current files were converted from the supplied 122Ag analysis so that an already-built scheme can be imported backwards into the working layer.
