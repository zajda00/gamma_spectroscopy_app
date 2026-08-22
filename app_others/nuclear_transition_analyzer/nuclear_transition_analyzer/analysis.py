from __future__ import annotations

from dataclasses import dataclass
from itertools import product
import math
from typing import Optional

import pandas as pd

from .data import ColumnMap, find_transition, icc_value, row_value
from .physics import (
    MULTIPOLES,
    allowed_for_candidates,
    lower_multipole_penalty,
    order_of_magnitude_distance,
    ratio_verdict,
    weisskopf_rate,
)
from .spin import exact_candidates, parse_spin_parity, suggested_candidates, summarize_spin


@dataclass(frozen=True)
class TransitionView:
    energy: float
    intensity_gamma: float
    from_energy: Optional[float]
    to_energy: Optional[float]
    from_spin_raw: str
    to_spin_raw: str
    from_spin_summary: str
    to_spin_summary: str


def transition_view(row: pd.Series, cmap: ColumnMap) -> TransitionView:
    return TransitionView(
        energy=float(row[cmap.energy]),
        intensity_gamma=float(row[cmap.intensity]),
        from_energy=float(row_value(row, cmap.from_energy, math.nan)) if row_value(row, cmap.from_energy, None) is not None else None,
        to_energy=float(row_value(row, cmap.to_energy, math.nan)) if row_value(row, cmap.to_energy, None) is not None else None,
        from_spin_raw=str(row_value(row, cmap.from_spin, "") or ""),
        to_spin_raw=str(row_value(row, cmap.to_spin, "") or ""),
        from_spin_summary=summarize_spin(row_value(row, cmap.from_spin, "")),
        to_spin_summary=summarize_spin(row_value(row, cmap.to_spin, "")),
    )


def _constraint_status(row: pd.Series, cmap: ColumnMap, multipole: str) -> tuple[str, str, float]:
    initial_raw = row_value(row, cmap.from_spin, "")
    final_raw = row_value(row, cmap.to_spin, "")
    exact_i = exact_candidates(initial_raw)
    exact_f = exact_candidates(final_raw)
    suggested_i = suggested_candidates(initial_raw)
    suggested_f = suggested_candidates(final_raw)

    # Hard rules only when both endpoints are exact.
    if exact_i and exact_f and not allowed_for_candidates(exact_i, exact_f, multipole):
        return "niemożliwe", "sprzeczne z pewnymi spinami/parzystościami", math.inf

    soft_penalty = 0.0
    notes = []
    if suggested_i and suggested_f:
        if allowed_for_candidates(suggested_i, suggested_f, multipole):
            notes.append("zgodne z sugerowanymi spinami")
            soft_penalty -= 0.05
        else:
            notes.append("koliduje z sugerowanymi spinami")
            soft_penalty += 0.35
    elif suggested_i or suggested_f:
        notes.append("spiny tylko częściowo sugerowane")

    if not notes:
        notes.append("brak twardego ograniczenia spinowego")
    return "dozwolone", "; ".join(notes), soft_penalty


def _same_initial_level(rows: list[pd.Series], cmap: ColumnMap, tolerance: float = 0.05) -> bool:
    if not cmap.from_energy:
        return False
    vals = [row_value(r, cmap.from_energy, None) for r in rows]
    if any(v is None for v in vals):
        return False
    vals = [float(v) for v in vals]
    return max(vals) - min(vals) <= tolerance


def _common_source_note(rows: list[pd.Series], cmap: ColumnMap, multipoles: tuple[str, ...]) -> tuple[str, float]:
    if not _same_initial_level(rows, cmap):
        return "różne poziomy początkowe albo brak danych o poziomie", 0.0
    # If from-spin is exact in at least one row, all rows should agree with it via hard checks already.
    # If lower spins are only suggestions, do not reject, only warn when every branch conflicts with suggestions.
    penalties = []
    for row, m in zip(rows, multipoles):
        _, note, p = _constraint_status(row, cmap, m)
        penalties.append(0 if "zgodne" in note else max(0, p))
    if all(p >= 0.35 for p in penalties) and penalties:
        return "wspólny poziom początkowy, ale wariant słabo wspierany przez sugerowane spiny", 0.2
    return "wspólny poziom początkowy, wariant nie łamie twardych reguł wyboru", 0.0


def compare_two_transitions(
    df: pd.DataFrame,
    cmap: ColumnMap,
    energy1: float,
    energy2: float,
    A: float = 122,
    tolerance_keV: float = 0.5,
    max_results: int = 20,
) -> tuple[list[TransitionView], pd.DataFrame]:
    row1 = find_transition(df, cmap, energy1, tolerance_keV)
    row2 = find_transition(df, cmap, energy2, tolerance_keV)
    rows = [row1, row2]
    t1, t2 = transition_view(row1, cmap), transition_view(row2, cmap)
    if t1.intensity_gamma <= 0 or t2.intensity_gamma <= 0:
        raise ValueError("Obie intensywności gamma muszą być dodatnie, żeby porównać stosunek.")

    observed_ratio = t1.intensity_gamma / t2.intensity_gamma
    records = []
    for m1, m2 in product(MULTIPOLES, repeat=2):
        s1, note1, p1 = _constraint_status(row1, cmap, m1)
        s2, note2, p2 = _constraint_status(row2, cmap, m2)
        if math.isinf(p1) or math.isinf(p2):
            continue
        r1 = weisskopf_rate(m1, t1.energy, A)
        r2 = weisskopf_rate(m2, t2.energy, A)
        predicted_ratio = r1 / r2 if r2 > 0 else math.inf
        dist = order_of_magnitude_distance(observed_ratio, predicted_ratio)
        common_note, common_penalty = _common_source_note(rows, cmap, (m1, m2))
        score = dist + lower_multipole_penalty(m1) + lower_multipole_penalty(m2) + p1 + p2 + common_penalty
        alpha1 = icc_value(row1, m1)
        alpha2 = icc_value(row2, m2)
        icc_note = ""
        if alpha1 is not None and alpha2 is not None and max(alpha1, alpha2) >= 0.10:
            icc_note = f"ICC może być istotne: alfa={alpha1:.3g}, {alpha2:.3g}"
        records.append(
            {
                "wariant": f"{m1} / {m2}",
                "I_gamma ratio": observed_ratio,
                "Weisskopf ratio": predicted_ratio,
                "różnica log10": dist,
                "ocena rzędu": ratio_verdict(dist),
                "reguły wyboru": f"{note1}; {note2}",
                "wspólny poziom": common_note,
                "uwaga ICC": icc_note,
                "score": score,
            }
        )
    out = pd.DataFrame(records)
    if out.empty:
        raise ValueError("Brak wariantów zgodnych z twardymi regułami wyboru.")
    out = out.sort_values(["score", "różnica log10"]).head(max_results).reset_index(drop=True)
    display_cols = ["wariant", "I_gamma ratio", "Weisskopf ratio", "różnica log10", "ocena rzędu", "reguły wyboru", "wspólny poziom", "uwaga ICC"]
    return [t1, t2], out[display_cols]


def scan_table(df: pd.DataFrame, cmap: ColumnMap, A: float = 122, max_rows: int = 200) -> pd.DataFrame:
    records = []
    for _, row in df.iterrows():
        try:
            tv = transition_view(row, cmap)
        except Exception:
            continue
        if not math.isfinite(tv.energy) or not math.isfinite(tv.intensity_gamma) or tv.intensity_gamma <= 0:
            continue
        exact_i = exact_candidates(tv.from_spin_raw)
        exact_f = exact_candidates(tv.to_spin_raw)
        suggested_i = suggested_candidates(tv.from_spin_raw)
        suggested_f = suggested_candidates(tv.to_spin_raw)
        hard_allowed = []
        soft_supported = []
        for m in MULTIPOLES:
            if exact_i and exact_f and not allowed_for_candidates(exact_i, exact_f, m):
                continue
            hard_allowed.append(m)
            if suggested_i and suggested_f and allowed_for_candidates(suggested_i, suggested_f, m):
                soft_supported.append(m)
        records.append(
            {
                "E_gamma keV": tv.energy,
                "z poziomu": tv.from_energy,
                "do poziomu": tv.to_energy,
                "I_gamma": tv.intensity_gamma,
                "spin pocz.": tv.from_spin_summary,
                "spin końc.": tv.to_spin_summary,
                "multipolowości twardo dozwolone": ", ".join(hard_allowed) if hard_allowed else "brak",
                "zgodne z sugestiami": ", ".join(soft_supported[:8]) if soft_supported else "brak / nie dotyczy",
            }
        )
    return pd.DataFrame(records).head(max_rows)
