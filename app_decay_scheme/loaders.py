from __future__ import annotations

import csv
from pathlib import Path
from typing import Any

from .beta_inputs import load_beta_inputs_from_text
from .models import Level, ProjectData, RenderSettings, Transition


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


class ProjectDataLoader:
    def load_project(self, folder: Path) -> ProjectData:
        folder = Path(folder)

        beta_inputs_path = self._pick_required(
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

        beta_inputs = load_beta_inputs_from_text(
            beta_inputs_path.read_text(encoding="utf-8")
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
        # 1. exact names first
        for name in exact_names:
            p = folder / name
            if p.exists() and p.is_file():
                return p

        # 2. then glob patterns
        for pattern in patterns:
            matches = sorted(
                [p for p in folder.glob(pattern) if p.is_file()],
                key=lambda p: p.name.lower(),
            )
            if matches:
                return matches[0]

        return None

    def _level_from_row(self, r: dict[str, Any]) -> Level:
        return Level(
            level_id=r.get("level_id", ""),
            nucleus=r.get("nucleus", ""),
            e_level_keV=float(r.get("E_level_keV", 0.0) or 0.0),
            de_level_keV=_float_or_none(r.get("dE_level_keV")),
            jpi=r.get("Jpi", ""),
            jpi_origin_year=r.get("Jpi_origin_year", ""),
            t12_s=_float_or_none(r.get("T12_s")),
            dt12_s=_float_or_none(r.get("dT12_s")),
            comments=r.get("comments", ""),
            in_scheme=str(r.get("in_scheme", "true")).lower() != "false",
        )

    def _transition_from_row(self, r: dict[str, Any]) -> Transition:
        return Transition(
            transition_id=r.get("transition_id", ""),
            peak_id=r.get("peak_id", ""),
            level_initial_id=r.get("level_initial_id", ""),
            level_final_id=r.get("level_final_id", ""),
            decay_parent=r.get("decay_parent", ""),
            decay_daughter=r.get("decay_daughter", ""),
            e_gamma_keV=float(r.get("e_gamma_keV", 0.0) or 0.0),
            de_gamma_keV=_float_or_none(r.get("de_gamma_keV")),
            e_level_initial_keV=_float_or_none(r.get("e_level_initial_keV")),
            e_level_final_keV=_float_or_none(r.get("e_level_final_keV")),
            origin=r.get("origin", r.get("current_origin_label", "")),
            quality_flag=r.get("quality_flag", "normal"),
            comment=r.get("comment", ""),
            integral_counts=_float_or_none(r.get("integral_counts")),
            integral_err=_float_or_none(r.get("integral_err")),
            relative_percent=_float_or_none(
                r.get("relative_percent", r.get("current_relative_percent"))
            ),
            relative_err_percent=_float_or_none(
                r.get("relative_err_percent", r.get("current_relative_err_percent"))
            ),
            absolute_percent=_float_or_none(
                r.get("absolute_percent", r.get("current_absolute_percent"))
            ),
            absolute_err_percent=_float_or_none(
                r.get("absolute_err_percent", r.get("current_absolute_err_percent"))
            ),
            emitted_plus_ic=_float_or_none(r.get("emitted_plus_ic")),
            emitted_plus_ic_err=_float_or_none(r.get("emitted_plus_ic_err")),
            alpha_tot=_float_or_none(r.get("alpha_tot")),
            multipolarity=r.get("multipolarity", ""),
            in_scheme=str(r.get("in_scheme", "true")).lower() != "false",
        )
