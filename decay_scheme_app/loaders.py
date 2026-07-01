from __future__ import annotations
import csv
from pathlib import Path
from typing import Any
from .beta_inputs import load_beta_inputs_from_text
from .models import Level, ProjectData, Transition


def _float_or_none(value: Any):
    s = str(value).strip() if value is not None else ''
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _read_csv(path: Path):
    with path.open(newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


class ProjectDataLoader:
    def load_project(self, folder: Path) -> ProjectData:
        folder = Path(folder)
        beta_inputs_path = self._pick_one(folder, ['beta_inputs_122Ag_122Cd.md', 'beta_inputs_122Ag_122Cd.yaml', 'beta_inputs.yaml', 'beta_inputs.md'])
        levels_path = self._pick_one(folder, ['levels_122Cd.csv', 'levels.csv'])
        transitions_path = self._pick_one(folder, ['transitions_122Ag_122Cd.csv', 'transitions.csv'])
        beta_inputs = load_beta_inputs_from_text(beta_inputs_path.read_text(encoding='utf-8'))
        levels = [self._level_from_row(r) for r in _read_csv(levels_path)]
        transitions = [self._transition_from_row(r) for r in _read_csv(transitions_path)]
        uncertain_rows = _read_csv(folder / '122Ag_non_scheme_uncertain_updated.csv') if (folder / '122Ag_non_scheme_uncertain_updated.csv').exists() else []
        literature_rows = _read_csv(folder / 'literature_compare_122Cd.csv') if (folder / 'literature_compare_122Cd.csv').exists() else []
        summary_rows = _read_csv(folder / 'non_scheme_gamma_category_summary_122Ag.csv') if (folder / 'non_scheme_gamma_category_summary_122Ag.csv').exists() else []
        priority_rows = _read_csv(folder / 'non_scheme_gamma_priority_peaks_122Ag.csv') if (folder / 'non_scheme_gamma_priority_peaks_122Ag.csv').exists() else []
        notes_text = (folder / 'analysis_notes.md').read_text(encoding='utf-8') if (folder / 'analysis_notes.md').exists() else ''
        return ProjectData(folder, beta_inputs, levels, transitions, uncertain_rows, literature_rows, summary_rows, priority_rows, notes_text)

    def _pick_one(self, folder: Path, names: list[str]) -> Path:
        for name in names:
            p = folder / name
            if p.exists():
                return p
        raise FileNotFoundError(f'Missing one of: {names} in {folder}')

    def _level_from_row(self, r: dict[str, Any]) -> Level:
        return Level(
            level_id=r.get('level_id', ''),
            nucleus=r.get('nucleus', ''),
            e_level_keV=float(r.get('E_level_keV', 0.0) or 0.0),
            de_level_keV=_float_or_none(r.get('dE_level_keV')),
            jpi=r.get('Jpi', ''),
            jpi_origin_year=r.get('Jpi_origin_year', ''),
            t12_s=_float_or_none(r.get('T12_s')),
            dt12_s=_float_or_none(r.get('dT12_s')),
            comments=r.get('comments', ''),
            in_scheme=str(r.get('in_scheme', 'true')).lower() != 'false',
        )

    def _transition_from_row(self, r: dict[str, Any]) -> Transition:
        return Transition(
            transition_id=r.get('transition_id', ''),
            peak_id=r.get('peak_id', ''),
            level_initial_id=r.get('level_initial_id', ''),
            level_final_id=r.get('level_final_id', ''),
            decay_parent=r.get('decay_parent', ''),
            decay_daughter=r.get('decay_daughter', ''),
            e_gamma_keV=float(r.get('e_gamma_keV', 0.0) or 0.0),
            de_gamma_keV=_float_or_none(r.get('de_gamma_keV')),
            e_level_initial_keV=_float_or_none(r.get('e_level_initial_keV')),
            e_level_final_keV=_float_or_none(r.get('e_level_final_keV')),
            origin=r.get('origin', r.get('current_origin_label', '')),
            quality_flag=r.get('quality_flag', 'normal'),
            comment=r.get('comment', ''),
            integral_counts=_float_or_none(r.get('integral_counts')),
            integral_err=_float_or_none(r.get('integral_err')),
            relative_percent=_float_or_none(r.get('relative_percent', r.get('current_relative_percent'))),
            relative_err_percent=_float_or_none(r.get('relative_err_percent', r.get('current_relative_err_percent'))),
            absolute_percent=_float_or_none(r.get('absolute_percent', r.get('current_absolute_percent'))),
            absolute_err_percent=_float_or_none(r.get('absolute_err_percent', r.get('current_absolute_err_percent'))),
            emitted_plus_ic=_float_or_none(r.get('emitted_plus_ic')),
            emitted_plus_ic_err=_float_or_none(r.get('emitted_plus_ic_err')),
            alpha_tot=_float_or_none(r.get('alpha_tot')),
            multipolarity=r.get('multipolarity', ''),
            in_scheme=str(r.get('in_scheme', 'true')).lower() != 'false',
        )
