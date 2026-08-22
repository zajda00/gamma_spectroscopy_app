from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd

ENERGY_COLUMNS = ["E (keV)", "E_gamma_keV", "energy_keV", "Energy (keV)"]
INTENSITY_COLUMNS = ["relative (%)", "intensity", "I_gamma", "Igamma", "I (%)"]
FROM_COLUMNS = ["from (keV)", "from_keV", "Ei", "initial_keV"]
TO_COLUMNS = ["onto (keV)", "to_keV", "Ef", "final_keV"]
FROM_SPIN_COLUMNS = ["from spin", "from_Jpi", "Ji", "initial spin"]
TO_SPIN_COLUMNS = ["onto spin", "to spin", "to_Jpi", "Jf", "final spin"]
ERR_COLUMNS = ["E error (keV)", "E_err", "energy_error_keV"]
ICC_COLUMNS = [f"{kind}{L}" for L in range(1, 6) for kind in ("E", "M")]


@dataclass(frozen=True)
class ColumnMap:
    energy: str
    intensity: str
    from_energy: Optional[str]
    to_energy: Optional[str]
    from_spin: Optional[str]
    to_spin: Optional[str]
    energy_error: Optional[str]


def _find(columns: list[str], candidates: list[str], required: bool = False, label: str = "kolumna") -> Optional[str]:
    normalized = {c.strip().lower(): c for c in columns}
    for cand in candidates:
        if cand.strip().lower() in normalized:
            return normalized[cand.strip().lower()]
    if required:
        raise ValueError(f"Nie znaleziono wymaganej kolumny: {label}. Dostępne kolumny: {', '.join(columns)}")
    return None


def detect_columns(df: pd.DataFrame, intensity_col: Optional[str] = None) -> ColumnMap:
    df = df.copy()
    df.columns = [str(c).strip() for c in df.columns]
    intensity_candidates = [intensity_col] + INTENSITY_COLUMNS if intensity_col else INTENSITY_COLUMNS
    return ColumnMap(
        energy=_find(list(df.columns), ENERGY_COLUMNS, True, "energia gamma") or "",
        intensity=_find(list(df.columns), intensity_candidates, True, "intensywność gamma") or "",
        from_energy=_find(list(df.columns), FROM_COLUMNS),
        to_energy=_find(list(df.columns), TO_COLUMNS),
        from_spin=_find(list(df.columns), FROM_SPIN_COLUMNS),
        to_spin=_find(list(df.columns), TO_SPIN_COLUMNS),
        energy_error=_find(list(df.columns), ERR_COLUMNS),
    )


def load_csv(path_or_buffer, intensity_col: Optional[str] = None) -> tuple[pd.DataFrame, ColumnMap]:
    df = pd.read_csv(path_or_buffer)
    df.columns = [str(c).strip() for c in df.columns]
    cmap = detect_columns(df, intensity_col=intensity_col)
    for col in [cmap.energy, cmap.intensity, cmap.from_energy, cmap.to_energy, cmap.energy_error]:
        if col and col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    return df, cmap


def find_transition(df: pd.DataFrame, cmap: ColumnMap, energy_keV: float, tolerance_keV: float = 0.5) -> pd.Series:
    energies = pd.to_numeric(df[cmap.energy], errors="coerce")
    diff = (energies - float(energy_keV)).abs()
    if diff.isna().all():
        raise ValueError("Kolumna energii nie zawiera poprawnych liczb.")
    idx = diff.idxmin()
    delta = float(diff.loc[idx])
    if delta > tolerance_keV:
        raise ValueError(f"Nie znaleziono przejścia blisko {energy_keV:.3f} keV. Najbliższa różnica: {delta:.3f} keV.")
    return df.loc[idx]


def row_value(row: pd.Series, col: Optional[str], default=None):
    if not col or col not in row.index:
        return default
    val = row[col]
    if pd.isna(val):
        return default
    return val


def icc_value(row: pd.Series, multipole: str) -> Optional[float]:
    if multipole in row.index and not pd.isna(row[multipole]):
        try:
            return float(row[multipole])
        except Exception:
            return None
    return None
