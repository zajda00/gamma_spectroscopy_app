from __future__ import annotations

import csv
import re
from pathlib import Path
from typing import Any

from .beta_inputs import load_beta_inputs_from_text
from .models import BetaInputs, Level, ProjectData, RenderSettings, Transition


def _float_or_none(value: Any):
    s = str(value).strip() if value is not None else ""
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _read_csv(path: Path):
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _normalize_field_name(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip().lower()
    text = text.replace('%', ' percent ')
    text = text.replace('/', ' ')
    text = text.replace('-', ' ')
    text = re.sub(r'[^a-z0-9]+', '_', text)
    text = re.sub(r'_+', '_', text).strip('_')
    return text


def _row_lookup(row: dict[str, Any], *candidates: str) -> Any:
    for candidate in candidates:
        if candidate in row:
            return row[candidate]
    return None


def _as_row_dict(raw_row: dict[str, Any]) -> dict[str, Any]:
    normalized: dict[str, Any] = {}
    for key, value in raw_row.items():
        if key is None:
            continue
        normalized[_normalize_field_name(key)] = value
    return normalized


class ProjectDataLoader:
    def _resolve_project_root(self, folder: Path) -> Path:
        folder = folder.resolve()
        if any(folder.glob('levels*.csv')) or (folder / 'levels.csv').exists():
            return folder
        if any(folder.glob('transitions*.csv')) or (folder / 'transitions.csv').exists():
            return folder

        for candidate in sorted(folder.iterdir(), key=lambda p: p.name.lower()):
            if not candidate.is_dir():
                continue
            if any(candidate.glob('levels*.csv')) or any(candidate.glob('transitions*.csv')):
                return candidate
        return folder

    def load_project(self, folder: Path) -> ProjectData:
        folder = self._resolve_project_root(Path(folder))

        beta_inputs_path = self._pick_optional(
            folder,
            exact_names=[
                "beta_inputs.yaml",
                "beta_inputs.yml",
                "beta_inputs.md",
            ],
            patterns=[
                "beta_inputs*.yaml",
                "beta_inputs*.yml",
                "beta_inputs*.md",
            ],
        )

        levels_path = self._pick_required(
            folder,
            exact_names=[
                "levels.csv",
            ],
            patterns=[
                "levels*.csv",
            ],
        )

        transitions_path = self._pick_required(
            folder,
            exact_names=[
                "transitions.csv",
            ],
            patterns=[
                "transitions*.csv",
            ],
        )

        uncertain_path = self._pick_optional(
            folder,
            exact_names=[
                "non_scheme_uncertain.csv",
                "uncertain.csv",
            ],
            patterns=[
                "*non_scheme*uncertain*.csv",
                "*uncertain*.csv",
            ],
        )

        literature_path = self._pick_optional(
            folder,
            exact_names=[
                "literature_compare.csv",
            ],
            patterns=[
                "literature_compare*.csv",
            ],
        )

        summary_path = self._pick_optional(
            folder,
            exact_names=[
                "non_scheme_category_summary.csv",
                "category_summary.csv",
            ],
            patterns=[
                "*category_summary*.csv",
                "*non_scheme*summary*.csv",
            ],
        )

        priority_path = self._pick_optional(
            folder,
            exact_names=[
                "non_scheme_priority_peaks.csv",
                "priority_peaks.csv",
            ],
            patterns=[
                "*priority_peaks*.csv",
                "*non_scheme*priority*.csv",
            ],
        )

        notes_path = self._pick_optional(
            folder,
            exact_names=[
                "analysis_notes.md",
                "notes.md",
            ],
            patterns=[
                "analysis_notes*.md",
                "notes*.md",
            ],
        )

        beta_inputs = (
            load_beta_inputs_from_text(beta_inputs_path.read_text(encoding="utf-8"))
            if beta_inputs_path is not None
            else self._default_beta_inputs(folder)
        )
        levels = [self._level_from_row(r) for r in _read_csv(levels_path)]
        transitions = [self._transition_from_row(r) for r in _read_csv(transitions_path)]

        uncertain_rows = _read_csv(uncertain_path) if uncertain_path else []
        literature_rows = _read_csv(literature_path) if literature_path else []
        summary_rows = _read_csv(summary_path) if summary_path else []
        priority_rows = _read_csv(priority_path) if priority_path else []
        notes_text = notes_path.read_text(encoding="utf-8") if notes_path else ""

        return ProjectData(
            root=folder,
            beta_inputs=beta_inputs,
            levels=levels,
            transitions=transitions,
            render_settings=RenderSettings(),
            uncertain_rows=uncertain_rows,
            literature_rows=literature_rows,
            category_summary_rows=summary_rows,
            priority_rows=priority_rows,
            analysis_notes_text=notes_text,
        )

    def _pick_required(
        self,
        folder: Path,
        exact_names: list[str],
        patterns: list[str],
    ) -> Path:
        path = self._pick_optional(folder, exact_names, patterns)
        if path is None:
            names_text = ", ".join(exact_names + patterns)
            raise FileNotFoundError(
                f"Missing required file in {folder}. "
                f"Tried exact names / patterns: {names_text}"
            )
        return path

    def _pick_optional(
        self,
        folder: Path,
        exact_names: list[str],
        patterns: list[str],
    ) -> Path | None:
        exact_lower = {name.lower() for name in exact_names}
        for candidate in sorted(folder.iterdir(), key=lambda p: p.name.lower()):
            if not candidate.is_file():
                continue
            if candidate.name.lower() in exact_lower:
                return candidate

        for pattern in patterns:
            matches = sorted(
                [p for p in folder.glob(pattern) if p.is_file()],
                key=lambda p: p.name.lower(),
            )
            if matches:
                return matches[0]

        return None

    def _level_from_row(self, r: dict[str, Any]) -> Level:
        row = _as_row_dict(r)
        level_origin = str(_row_lookup(row, 'level_origin', 'level_origin_value', 'level_origin_label') or '').strip()
        certain_value = _row_lookup(row, 'certain', 'is_certain', 'certainty')
        certain = True
        if certain_value is not None:
            certain = str(certain_value).strip().lower() in {'yes', 'y', 'true', '1'}
        return Level(
            level_id=str(_row_lookup(row, 'level_id', 'levelid') or ''),
            nucleus=str(_row_lookup(row, 'nucleus', 'nuclide') or ''),
            e_level_keV=float(_row_lookup(row, 'e_level_kev', 'e_level_ke_v', 'e_level_kev_value', 'e_level_ke_v_value') or 0.0),
            de_level_keV=_float_or_none(_row_lookup(row, 'de_level_kev', 'd_e_level_ke_v', 'd_e_level_kev')),
            jpi=str(_row_lookup(row, 'jpi', 'j_pi') or ''),
            jpi_origin_year=str(_row_lookup(row, 'jpi_origin_year', 'jpi_origin') or ''),
            t12_s=_float_or_none(_row_lookup(row, 't12_s', 't12', 'half_life_s')),
            dt12_s=_float_or_none(_row_lookup(row, 'dt12_s', 'd_t12_s', 'dhalf_life_s')),
            comments=str(_row_lookup(row, 'comments', 'comment', 'notes') or ''),
            in_scheme=str(_row_lookup(row, 'in_scheme', 'include_in_scheme') or 'true').lower() != 'false',
            level_origin=level_origin,
            certain=certain,
        )

    def _transition_from_row(self, r: dict[str, Any]) -> Transition:
        row = _as_row_dict(r)
        return Transition(
            transition_id=str(_row_lookup(row, 'transition_id', 'transitionid') or ''),
            peak_id=str(_row_lookup(row, 'peak_id', 'peak') or ''),
            level_initial_id=str(_row_lookup(row, 'level_initial_id', 'level_initial', 'initial_level_id') or ''),
            level_final_id=str(_row_lookup(row, 'level_final_id', 'level_final', 'final_level_id') or ''),
            decay_parent=str(_row_lookup(row, 'decay_parent', 'parent_nucleus') or ''),
            decay_daughter=str(_row_lookup(row, 'decay_daughter', 'daughter_nucleus') or ''),
            e_gamma_keV=float(_row_lookup(row, 'e_gamma_kev', 'gamma_energy_ke_v', 'gamma_ke_v') or 0.0),
            de_gamma_keV=_float_or_none(_row_lookup(row, 'de_gamma_kev', 'd_gamma_ke_v')),
            e_level_initial_keV=_float_or_none(_row_lookup(row, 'e_level_initial_kev', 'e_initial_level_ke_v')),
            e_level_final_keV=_float_or_none(_row_lookup(row, 'e_level_final_kev', 'e_final_level_ke_v')),
            origin=str(_row_lookup(row, 'origin', 'current_origin_label', 'source') or ''),
            quality_flag=str(_row_lookup(row, 'quality_flag', 'quality') or 'normal'),
            comment=str(_row_lookup(row, 'comment', 'notes', 'comments') or ''),
            integral_counts=_float_or_none(_row_lookup(row, 'integral_counts', 'counts')),
            integral_err=_float_or_none(_row_lookup(row, 'integral_err', 'd_integral_counts')),
            relative_percent=_float_or_none(
                _row_lookup(row, 'relative_percent', 'relative_percent_value', 'relative', 'relative_percent_value_percent', 'relative_percent_')
            ),
            relative_err_percent=_float_or_none(
                _row_lookup(row, 'relative_err_percent', 'relative_error_percent', 'd_relative_percent')
            ),
            absolute_percent=_float_or_none(
                _row_lookup(row, 'absolute_percent', 'absolute_percent_value', 'absolute', 'absolute_percent_value_percent', 'absolute_percent_')
            ),
            absolute_err_percent=_float_or_none(
                _row_lookup(row, 'absolute_err_percent', 'absolute_error_percent', 'd_absolute_percent')
            ),
            emitted_plus_ic=_float_or_none(_row_lookup(row, 'emitted_plus_ic', 'emitted_ic')),
            emitted_plus_ic_err=_float_or_none(_row_lookup(row, 'emitted_plus_ic_err', 'd_emitted_plus_ic')),
            alpha_tot=_float_or_none(_row_lookup(row, 'alpha_tot', 'alpha_total')),
            multipolarity=str(_row_lookup(row, 'multipolarity', 'mult') or ''),
            in_scheme=str(_row_lookup(row, 'in_scheme', 'include_in_scheme') or 'true').lower() != 'false',
        )

    def _default_beta_inputs(self, folder: Path) -> BetaInputs:
        """Return a minimal in-memory beta-input config when no batch file is present.

        This keeps the project loadable for simplified workflow where the user enters
        basic decay info directly in the app and not through a static YAML file.
        """
        folder_name = folder.name
        parent_nucleus = ""
        daughter_nucleus = ""

        m = re.search(r"(?P<parent>\d+[A-Za-z]+)[_-](?P<daughter>\d+[A-ZaZ]+)", folder_name)
        if m:
            parent_nucleus = m.group('parent')
            daughter_nucleus = m.group('daughter')

        return BetaInputs(
            raw={},
            parent_nucleus=parent_nucleus,
            daughter_nucleus=daughter_nucleus,
            qbeta_keV=0.0,
            dqbeta_keV=0.0,
            parent_states=[],
            mother_spin_display='',
            mother_half_life_display='',
            neutron_separation_energy_keV=None,
            show_neutron_separation=False,
            ground_state_strategy='manual_or_none',
            normalization_reference_keV=None,
            mother_a=0,
            mother_z=0,
            mother_n=0,
            daughter_a=0,
            daughter_z=0,
            daughter_n=0,
            mother_t12='',
            mother_spinpar='',
            mother_q='',
            mother_sn='',
            mother_pn='',
            decay_channel=2,
            separation_energy_type='n',
        )
