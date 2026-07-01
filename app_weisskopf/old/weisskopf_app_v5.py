import argparse
import itertools
import math
import os
import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np
import pandas as pd

try:
    import tkinter as tk
    import tkinter.font as tkfont
    from tkinter import filedialog, messagebox, ttk
except Exception:
    tk = None
    tkfont = None
    filedialog = None
    messagebox = None
    ttk = None

ALL_MULTIPOLES = [f"{kind}{L}" for L in range(1, 6) for kind in ("E", "M")]
DEFAULT_INTENSITY_COL = "relative (%)"
DEFAULT_PATH = "AI_processing_2_v2.csv"
PRIOR_WEIGHT = 0.08
TOP_N = 3


@dataclass(frozen=True)
class JPi:
    J: int
    parity: Optional[str] = None

    def label(self) -> str:
        return f"{self.J}{self.parity or '?'}"


@dataclass
class TransitionInfo:
    energy_keV: float
    from_keV: float
    to_keV: float
    from_spin_raw: str
    onto_spin_raw: str
    from_spin_parsed: str
    onto_spin_parsed: str
    intensity: float
    row_index: int


def double_factorial(n: int) -> int:
    if n <= 0:
        return 1
    result = 1
    for k in range(n, 0, -2):
        result *= k
    return result


def parse_spin_candidates(value) -> List[JPi]:
    if pd.isna(value):
        return []
    s = str(value).strip()
    if not s or s.lower() == "nan":
        return []

    s = s.replace("−", "-").replace("–", "-").replace(" ", "")
    s = re.sub(r"(\d+)\(\+\)", r"\1+", s)
    s = re.sub(r"(\d+)\(-\)", r"\1-", s)
    s = s.replace("[", "(").replace("]", ")")

    groups = re.split(r"(?<=\)),(?=\()|/|;|\bor\b", s)
    out: List[JPi] = []

    for group in groups:
        g = group.strip().strip("()")
        if not g:
            continue
        tokens = [tok for tok in g.split(",") if tok]
        explicit_signs = []
        for tok in tokens:
            m_sign = re.search(r"([+-])$", tok)
            if m_sign:
                explicit_signs.append(m_sign.group(1))
        shared_sign = explicit_signs[-1] if len(set(explicit_signs)) == 1 and explicit_signs else None

        for tok in tokens:
            m = re.search(r"(\d+)([+-])?", tok)
            if not m:
                continue
            cand = JPi(int(m.group(1)), m.group(2) or shared_sign)
            if cand not in out:
                out.append(cand)
    return out


def spin_label(value) -> str:
    cands = parse_spin_candidates(value)
    if not cands:
        return "brak / niepewne"
    return ", ".join(c.label() for c in cands)


def parity_allows(kind: str, L: int, pi_i: Optional[str], pi_f: Optional[str]) -> bool:
    if pi_i is None or pi_f is None:
        return True
    same = pi_i == pi_f
    if kind == "E":
        return same if (L % 2 == 0) else (not same)
    return (not same) if (L % 2 == 0) else same


def allowed_multipoles(from_spin, to_spin, max_L: int = 5) -> List[str]:
    initial = parse_spin_candidates(from_spin)
    final = parse_spin_candidates(to_spin)
    if not initial or not final:
        return ALL_MULTIPOLES[: 2 * max_L]

    allowed = set()
    for si in initial:
        for sf in final:
            min_L = max(1, abs(si.J - sf.J))
            max_allowed = min(max_L, si.J + sf.J)
            for L in range(min_L, max_allowed + 1):
                if si.J == 0 and sf.J == 0:
                    continue
                for kind in ("E", "M"):
                    if parity_allows(kind, L, si.parity, sf.parity):
                        allowed.add(f"{kind}{L}")
    return sorted(allowed, key=lambda x: (int(x[1:]), x[0]))


def multipole_L(multipole: str) -> int:
    return int(multipole[1:])


def prior_penalty(multipoles: Sequence[str], weight: float = PRIOR_WEIGHT) -> float:
    return weight * sum(max(0, multipole_L(m) - 1) for m in multipoles)


def weisskopf_B(multipole: str, A: float) -> float:
    kind = multipole[0]
    L = multipole_L(multipole)
    if kind == "E":
        return (1.2 ** (2 * L) / (4 * math.pi)) * ((3 / (L + 3)) ** 2) * (A ** (2 * L / 3))
    return (10 / math.pi) * (1.2 ** (2 * L - 2)) * ((3 / (L + 3)) ** 2) * (A ** ((2 * L - 2) / 3))


def lambda_wu(multipole: str, E_keV: float, A: float) -> float:
    E_MeV = float(E_keV) / 1000.0
    kind = multipole[0]
    L = multipole_L(multipole)
    pref = 5.50e22 if kind == "E" else 6.08e20
    geom = (L + 1) / (L * (double_factorial(2 * L + 1) ** 2))
    energy = (E_MeV / 197.3) ** (2 * L + 1)
    return pref * geom * energy * weisskopf_B(multipole, A)


def find_energy_column(df: pd.DataFrame) -> str:
    for candidate in ["E (keV)", "E_gamma_keV", "energy_keV"]:
        if candidate in df.columns:
            return candidate
    raise KeyError("Nie znaleziono kolumny z energią przejścia.")


def load_dataframe(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df.columns = [c.strip() for c in df.columns]
    return df


def find_transition(df: pd.DataFrame, energy_keV: float, tol_keV: float = 0.4) -> pd.Series:
    e_col = find_energy_column(df)
    diff = (pd.to_numeric(df[e_col], errors="coerce") - float(energy_keV)).abs()
    idx = diff.idxmin()
    if float(diff.loc[idx]) > tol_keV:
        raise ValueError(
            f"Nie znaleziono przejścia w pobliżu {energy_keV} keV, najbliższe ma odchyłkę {diff.loc[idx]:.3f} keV"
        )
    return df.loc[idx]


def icc_value(row: pd.Series, multipole: str) -> float:
    if multipole in row.index and pd.notna(row[multipole]):
        return float(row[multipole])
    return 0.0


def icc_recommendation(alpha: float) -> str:
    if alpha < 0.02:
        return "pomijalne"
    if alpha < 0.10:
        return "małe, uwzględnij do precyzyjnego bilansu"
    if alpha < 0.50:
        return "istotne, warto uwzględnić"
    return "duże, należy uwzględnić"


def infer_basis_from_alphas(alphas: Sequence[float]) -> str:
    arr = np.array(alphas, dtype=float)
    max_alpha = float(np.max(arr)) if arr.size else 0.0
    return "total" if max_alpha >= 0.10 else "gamma"


def safe_log_distance(pred: np.ndarray, obs: np.ndarray) -> float:
    eps = 1e-15
    pred = np.clip(pred.astype(float), eps, None)
    obs = np.clip(obs.astype(float), eps, None)
    return float(np.mean(np.abs(np.log10(pred / obs))))


def normalize(values: Sequence[float]) -> np.ndarray:
    arr = np.array(values, dtype=float)
    s = arr.sum()
    if s <= 0:
        raise ValueError("Suma intensywności musi być dodatnia.")
    return arr / s


def compute_pairwise_ratios(
    values: Sequence[float],
    labels: Optional[Sequence[str]] = None,
    name_prefix: str = "ratio",
) -> Tuple[Dict[str, float], str]:
    arr = np.array(values, dtype=float)
    out: Dict[str, float] = {}
    parts: List[str] = []
    for i, j in itertools.combinations(range(len(arr)), 2):
        ratio = float(arr[i] / arr[j])
        out[f"{name_prefix}_{i+1}_{j+1}"] = ratio
        if labels:
            parts.append(f"{labels[i]}/{labels[j]}={ratio:.6g}")
        else:
            parts.append(f"{i+1}/{j+1}={ratio:.6g}")
    return out, "; ".join(parts)


def row_ratio_lines(row: pd.Series, n_transitions: int) -> List[str]:
    lines: List[str] = []
    for i, j in itertools.combinations(range(1, n_transitions + 1), 2):
        e_i = row.get(f"E{i}_keV")
        e_j = row.get(f"E{j}_keV")
        label = f"{e_i:.2f}/{e_j:.2f} keV"
        g_obs = row.get(f"gamma_ratio_obs_{i}_{j}")
        g_pred = row.get(f"gamma_ratio_pred_{i}_{j}")
        t_obs = row.get(f"total_ratio_obs_{i}_{j}")
        t_pred = row.get(f"total_ratio_pred_{i}_{j}")
        lines.append(f"   {label}: I_gamma={g_obs:.6g}, W.u.={g_pred:.6g}")
        lines.append(f"   {label} po ICC: I_tot={t_obs:.6g}, W.u._tot={t_pred:.6g}")
    return lines


def row_to_transition_info(row: pd.Series, intensity_col: str, row_index: int) -> TransitionInfo:
    return TransitionInfo(
        energy_keV=float(row[find_energy_column(pd.DataFrame([row]))]),
        from_keV=float(row.get("from (keV)", np.nan)),
        to_keV=float(row.get("onto (keV)", np.nan)),
        from_spin_raw=str(row.get("from spin", "") or ""),
        onto_spin_raw=str(row.get("onto spin", "") or ""),
        from_spin_parsed=spin_label(row.get("from spin")),
        onto_spin_parsed=spin_label(row.get("onto spin")),
        intensity=float(row.get(intensity_col, np.nan)),
        row_index=row_index,
    )


def candidates_include_zero_plus(value) -> bool:
    return any(c.J == 0 and c.parity == "+" for c in parse_spin_candidates(value))


def transition_has_zero_plus_endpoint(row: pd.Series) -> bool:
    return candidates_include_zero_plus(row.get("from spin")) or candidates_include_zero_plus(row.get("onto spin"))


def zero_plus_pair_constraint_flags(rows: Sequence[pd.Series]) -> List[bool]:
    return [transition_has_zero_plus_endpoint(row) for row in rows]


def multipoles_respect_zero_plus_constraint(multipoles: Sequence[str], zero_plus_flags: Sequence[bool]) -> bool:
    constrained = [m for m, flag in zip(multipoles, zero_plus_flags) if flag]
    if len(constrained) < 2:
        return True
    return len(set(constrained)) == 1


def parity_symbol_product(pi_f: Optional[str], kind: str, L: int) -> Optional[str]:
    if pi_f is None:
        return None
    if kind == "E":
        return pi_f if (L % 2 == 0) else ("-" if pi_f == "+" else "+")
    return ("-" if pi_f == "+" else "+") if (L % 2 == 0) else pi_f


def candidate_initial_spins_for_transition(row: pd.Series, multipole: str, max_J: int = 10) -> Dict[int, List[Optional[str]]]:
    kind = multipole[0]
    L = multipole_L(multipole)
    finals = parse_spin_candidates(row.get("onto spin"))
    initials = parse_spin_candidates(row.get("from spin"))

    if not finals:
        if initials:
            out: Dict[int, List[Optional[str]]] = {}
            for cand in initials:
                out.setdefault(cand.J, []).append(cand.parity)
            return out
        return {}

    candidate_js = sorted({cand.J for cand in initials}) if initials else list(range(0, max_J + 1))
    allowed: Dict[int, List[Optional[str]]] = {}
    for Ji in candidate_js:
        allowed_parities: List[Optional[str]] = []
        if initials:
            initial_parities = [cand.parity for cand in initials if cand.J == Ji]
        else:
            inferred = []
            for sf in finals:
                parity_guess = parity_symbol_product(sf.parity, kind, L)
                if parity_guess is not None and parity_guess not in inferred:
                    inferred.append(parity_guess)
            initial_parities = inferred if inferred else [None, "+", "-"]
        if not initial_parities:
            continue
        for pi_i in initial_parities:
            for sf in finals:
                if Ji == 0 and sf.J == 0:
                    continue
                if not (abs(Ji - sf.J) <= L <= Ji + sf.J):
                    continue
                if not parity_allows(kind, L, pi_i, sf.parity):
                    continue
                if pi_i not in allowed_parities:
                    allowed_parities.append(pi_i)
        if allowed_parities:
            allowed[Ji] = allowed_parities
    return allowed


def common_initial_state_candidates(rows: Sequence[pd.Series], multipoles: Sequence[str], max_J: int = 10) -> Tuple[bool, set[Tuple[int, Optional[str]]]]:
    candidate_maps = [candidate_initial_spins_for_transition(row, m, max_J=max_J) for row, m in zip(rows, multipoles)]
    if not candidate_maps or any(not cmap for cmap in candidate_maps):
        return False, set()

    state_sets = []
    for cmap in candidate_maps:
        states = set()
        for J, parities in cmap.items():
            for parity in parities:
                states.add((J, parity))
        state_sets.append(states)
    return True, set.intersection(*state_sets)


def common_initial_spin_metrics(rows: Sequence[pd.Series], multipoles: Sequence[str], max_J: int = 10) -> Dict[str, object]:
    active, common_states = common_initial_state_candidates(rows, multipoles, max_J=max_J)
    if not active:
        return {"active": False, "has_common_state": False, "penalty": 0.0, "best_J": None, "best_parity": None, "best_max_dJ": None, "best_mean_dJ": None}

    if not common_states:
        return {"active": True, "has_common_state": False, "penalty": 0.35, "best_J": None, "best_parity": None, "best_max_dJ": None, "best_mean_dJ": None}

    finals_per_row = [parse_spin_candidates(row.get("onto spin")) for row in rows]
    candidates = []
    for Ji, pi_i in sorted(common_states, key=lambda x: (x[0], '' if x[1] is None else x[1])):
        djs = []
        for row, multipole, finals in zip(rows, multipoles, finals_per_row):
            kind = multipole[0]
            L = multipole_L(multipole)
            valid = []
            for sf in finals:
                if Ji == 0 and sf.J == 0:
                    continue
                if not (abs(Ji - sf.J) <= L <= Ji + sf.J):
                    continue
                if not parity_allows(kind, L, pi_i, sf.parity):
                    continue
                valid.append(abs(Ji - sf.J))
            if not valid:
                break
            djs.append(min(valid))
        if len(djs) == len(rows):
            max_dj = max(djs)
            mean_dj = float(sum(djs)) / len(djs)
            penalty = 0.03 * max_dj + 0.01 * mean_dj
            candidates.append((penalty, max_dj, mean_dj, Ji, pi_i))

    if not candidates:
        return {"active": True, "has_common_state": False, "penalty": 0.35, "best_J": None, "best_parity": None, "best_max_dJ": None, "best_mean_dJ": None}

    penalty, max_dj, mean_dj, Ji, pi_i = min(candidates, key=lambda x: (x[0], x[1], x[2], x[3], '' if x[4] is None else x[4]))
    return {"active": True, "has_common_state": True, "penalty": penalty, "best_J": Ji, "best_parity": pi_i, "best_max_dJ": max_dj, "best_mean_dJ": mean_dj}


def transition_summary(row: pd.Series, intensity_col: str = DEFAULT_INTENSITY_COL) -> Dict[str, object]:
    allowed = allowed_multipoles(row.get("from spin"), row.get("onto spin"), max_L=5)
    info = row_to_transition_info(row, intensity_col, row.name if row.name is not None else -1)
    return {
        "E_keV": info.energy_keV,
        "from_keV": info.from_keV,
        "to_keV": info.to_keV,
        "from_spin_raw": info.from_spin_raw,
        "onto_spin_raw": info.onto_spin_raw,
        "from_spin_parsed": info.from_spin_parsed,
        "onto_spin_parsed": info.onto_spin_parsed,
        "allowed_multipoles": ", ".join(allowed),
        "intensity": info.intensity,
    }


def analyze_transition_set(
    rows: Sequence[pd.Series],
    A: float,
    intensity_col: str,
    prefer_low_L: bool = True,
) -> Tuple[Dict[str, object], pd.DataFrame]:
    if len(rows) < 2:
        raise ValueError("Potrzebne są co najmniej dwa przejścia.")

    infos = [row_to_transition_info(row, intensity_col, int(row.name) if row.name is not None else idx) for idx, row in enumerate(rows)]
    intensities = np.array([info.intensity for info in infos], dtype=float)
    if np.any(intensities <= 0):
        raise ValueError("Wszystkie intensywności muszą być dodatnie.")

    from_values = [info.from_keV for info in infos]
    same_initial = all(math.isclose(from_values[0], val, abs_tol=1e-6) for val in from_values[1:])
    allowed_lists = [allowed_multipoles(row.get("from spin"), row.get("onto spin"), max_L=5) for row in rows]
    zero_plus_flags = zero_plus_pair_constraint_flags(rows)
    zero_plus_constraint_active = sum(bool(x) for x in zero_plus_flags) >= 2
    observed_gamma_fracs = normalize(intensities)
    energy_labels = [f"I({info.energy_keV:.2f})" for info in infos]
    observed_gamma_ratio_rec, observed_gamma_ratio_summary = compute_pairwise_ratios(
        intensities, labels=energy_labels, name_prefix="gamma_ratio_obs"
    )

    results = []
    for multipoles in itertools.product(*allowed_lists):
        if not multipoles_respect_zero_plus_constraint(multipoles, zero_plus_flags):
            continue
        lambdas = np.array([lambda_wu(m, info.energy_keV, A) for m, info in zip(multipoles, infos)], dtype=float)
        alphas = np.array([icc_value(row, m) for row, m in zip(rows, multipoles)], dtype=float)

        pred_gamma_fracs = normalize(lambdas)
        gamma_mismatch = safe_log_distance(pred_gamma_fracs, observed_gamma_fracs)

        observed_total = intensities * (1.0 + alphas)
        pred_total = lambdas * (1.0 + alphas)
        observed_total_fracs = normalize(observed_total)
        pred_total_fracs = normalize(pred_total)
        total_mismatch = safe_log_distance(pred_total_fracs, observed_total_fracs)

        gamma_pred_ratio_rec, gamma_pred_ratio_summary = compute_pairwise_ratios(
            lambdas,
            labels=[f"W.u.({info.energy_keV:.2f})" for info in infos],
            name_prefix="gamma_ratio_pred",
        )
        total_obs_ratio_rec, total_obs_ratio_summary = compute_pairwise_ratios(
            observed_total,
            labels=[f"I_tot({info.energy_keV:.2f})" for info in infos],
            name_prefix="total_ratio_obs",
        )
        total_pred_ratio_rec, total_pred_ratio_summary = compute_pairwise_ratios(
            pred_total,
            labels=[f"W.u._tot({info.energy_keV:.2f})" for info in infos],
            name_prefix="total_ratio_pred",
        )

        basis = infer_basis_from_alphas(alphas)
        effective_mismatch = total_mismatch if basis == "total" else gamma_mismatch
        p_penalty = prior_penalty(multipoles) if prefer_low_L else 0.0
        spin_metrics = common_initial_spin_metrics(rows, multipoles)
        if same_initial and spin_metrics["active"] and not spin_metrics["has_common_state"]:
            continue
        spin_penalty = spin_metrics["penalty"] if same_initial and spin_metrics["has_common_state"] else 0.0
        combined = effective_mismatch + p_penalty + spin_penalty

        results.append(
            {
                "n_transitions": len(rows),
                "from_keV": infos[0].from_keV,
                "same_initial_level": same_initial,
                "multipoles": " | ".join(multipoles),
                "multipoles_compact": "/".join(multipoles),
                "prior_penalty": p_penalty,
                "spin_penalty": spin_penalty,
                "preferred_initial_spin": spin_metrics["best_J"],
                "preferred_initial_parity": spin_metrics["best_parity"],
                "preferred_initial_spin_max_dJ": spin_metrics["best_max_dJ"],
                "preferred_initial_spin_mean_dJ": spin_metrics["best_mean_dJ"],
                "comparison_basis": basis,
                "effective_mismatch": effective_mismatch,
                "gamma_mismatch": gamma_mismatch,
                "total_mismatch": total_mismatch,
                "combined_score": combined,
                "max_alpha": float(np.max(alphas)),
                "alpha_summary": ", ".join(f"{a:.4g}" for a in alphas),
                "intensity_summary": ", ".join(f"{x:.4g}" for x in intensities),
                "energy_summary": ", ".join(f"{info.energy_keV:.2f}" for info in infos),
                "observed_gamma_ratio_summary": observed_gamma_ratio_summary,
                "predicted_gamma_ratio_summary": gamma_pred_ratio_summary,
                "observed_total_ratio_summary": total_obs_ratio_summary,
                "predicted_total_ratio_summary": total_pred_ratio_summary,
                **{f"E{i+1}_keV": info.energy_keV for i, info in enumerate(infos)},
                **{f"I{i+1}": float(intensities[i]) for i in range(len(infos))},
                **{f"m{i+1}": multipoles[i] for i in range(len(multipoles))},
                **{f"alpha{i+1}": float(alphas[i]) for i in range(len(alphas))},
                **{f"gamma_frac_obs_{i+1}": float(observed_gamma_fracs[i]) for i in range(len(observed_gamma_fracs))},
                **{f"gamma_frac_pred_{i+1}": float(pred_gamma_fracs[i]) for i in range(len(pred_gamma_fracs))},
                **{f"total_frac_obs_{i+1}": float(observed_total_fracs[i]) for i in range(len(observed_total_fracs))},
                **{f"total_frac_pred_{i+1}": float(pred_total_fracs[i]) for i in range(len(pred_total_fracs))},
                **observed_gamma_ratio_rec,
                **gamma_pred_ratio_rec,
                **total_obs_ratio_rec,
                **total_pred_ratio_rec,
            }
        )

    if not results:
        raise ValueError("Po uwzględnieniu ograniczenia dla przejść związanych z 0+ nie pozostała żadna dozwolona kombinacja multipolowości.")

    ranked = pd.DataFrame(results).sort_values(
        ["combined_score", "effective_mismatch", "prior_penalty", "gamma_mismatch", "total_mismatch", "multipoles_compact"]
    ).reset_index(drop=True)

    meta = {
        "n_transitions": len(rows),
        "same_initial_level": same_initial,
        "observed_gamma_fractions": observed_gamma_fracs,
        "observed_gamma_ratio_summary": observed_gamma_ratio_summary,
        "zero_plus_constraint_active": zero_plus_constraint_active,
        "note": (
            "Zestaw pochodzi z tego samego poziomu początkowego, więc porównanie branchingu jest sensowne."
            if same_initial
            else "Uwaga: wybrane przejścia nie schodzą z tego samego poziomu, więc wynik jest tylko heurystyką."
        ),
        "transitions": [transition_summary(row, intensity_col=intensity_col) for row in rows],
    }
    return meta, ranked


def compare_two_transitions(
    df: pd.DataFrame,
    energy_1: float,
    energy_2: float,
    A: float = 122,
    intensity_col: str = DEFAULT_INTENSITY_COL,
    tol_keV: float = 0.4,
) -> Tuple[Dict[str, object], pd.DataFrame]:
    rows = [find_transition(df, energy_1, tol_keV), find_transition(df, energy_2, tol_keV)]
    return analyze_transition_set(rows, A=A, intensity_col=intensity_col)


def compare_three_transitions(
    df: pd.DataFrame,
    energy_1: float,
    energy_2: float,
    energy_3: float,
    A: float = 122,
    intensity_col: str = DEFAULT_INTENSITY_COL,
    tol_keV: float = 0.4,
) -> Tuple[Dict[str, object], pd.DataFrame]:
    rows = [
        find_transition(df, energy_1, tol_keV),
        find_transition(df, energy_2, tol_keV),
        find_transition(df, energy_3, tol_keV),
    ]
    return analyze_transition_set(rows, A=A, intensity_col=intensity_col)


def build_pairwise_dataset(df: pd.DataFrame, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    rows_out = []
    for from_energy, grp in df.groupby("from (keV)", dropna=True):
        grp = grp.copy()
        grp = grp[pd.to_numeric(grp[intensity_col], errors="coerce") > 0]
        if len(grp) < 2:
            continue
        for i, j in itertools.combinations(list(grp.index), 2):
            meta, ranked = analyze_transition_set([df.loc[i], df.loc[j]], A=A, intensity_col=intensity_col)
            top3 = ranked.head(TOP_N)
            best = top3.iloc[0]
            row_i = df.loc[i]
            row_j = df.loc[j]
            rows_out.append(
                {
                    "from_keV": float(from_energy),
                    "E1_keV": float(row_i[e_col]),
                    "E2_keV": float(row_j[e_col]),
                    "I1": float(row_i[intensity_col]),
                    "I2": float(row_j[intensity_col]),
                    "best_multipole_1": best["m1"],
                    "best_multipole_2": best["m2"],
                    "best_basis": best["comparison_basis"],
                    "best_combined_score": best["combined_score"],
                    "best_gamma_mismatch": best["gamma_mismatch"],
                    "best_total_mismatch": best["total_mismatch"],
                    "best_prior_penalty": best["prior_penalty"],
                    "obs_ratio_gamma": best["gamma_ratio_obs_1_2"],
                    "pred_ratio_gamma_best": best["gamma_ratio_pred_1_2"],
                    "obs_ratio_total_best": best["total_ratio_obs_1_2"],
                    "pred_ratio_total_best": best["total_ratio_pred_1_2"],
                    "ratio_summary_best": f"I={best['gamma_ratio_obs_1_2']:.6g}, W.u.={best['gamma_ratio_pred_1_2']:.6g}, I_tot={best['total_ratio_obs_1_2']:.6g}, W.u._tot={best['total_ratio_pred_1_2']:.6g}",
                    "alpha1": best["alpha1"],
                    "alpha2": best["alpha2"],
                    "icc1_recommendation": icc_recommendation(best["alpha1"]),
                    "icc2_recommendation": icc_recommendation(best["alpha2"]),
                    "allowed_1": ", ".join(allowed_multipoles(row_i.get("from spin"), row_i.get("onto spin"))),
                    "allowed_2": ", ".join(allowed_multipoles(row_j.get("from spin"), row_j.get("onto spin"))),
                    "top3": " || ".join(
                        f"{row['multipoles_compact']} [score={row['combined_score']:.3f}, basis={row['comparison_basis']}]"
                        for _, row in top3.iterrows()
                    ),
                    "same_initial_level": meta["same_initial_level"],
                }
            )
    return pd.DataFrame(rows_out).sort_values(["from_keV", "best_combined_score", "E1_keV", "E2_keV"]) if rows_out else pd.DataFrame()


def build_triplet_dataset(df: pd.DataFrame, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    rows_out = []
    for from_energy, grp in df.groupby("from (keV)", dropna=True):
        grp = grp.copy()
        grp = grp[pd.to_numeric(grp[intensity_col], errors="coerce") > 0]
        if len(grp) < 3:
            continue
        for combo in itertools.combinations(list(grp.index), 3):
            subset = [df.loc[idx] for idx in combo]
            meta, ranked = analyze_transition_set(subset, A=A, intensity_col=intensity_col)
            top3 = ranked.head(TOP_N)
            best = top3.iloc[0]
            energies = [float(df.loc[idx][e_col]) for idx in combo]
            intensities = [float(df.loc[idx][intensity_col]) for idx in combo]
            rows_out.append(
                {
                    "from_keV": float(from_energy),
                    "E1_keV": energies[0],
                    "E2_keV": energies[1],
                    "E3_keV": energies[2],
                    "I1": intensities[0],
                    "I2": intensities[1],
                    "I3": intensities[2],
                    "best_m1": best["m1"],
                    "best_m2": best["m2"],
                    "best_m3": best["m3"],
                    "best_basis": best["comparison_basis"],
                    "best_combined_score": best["combined_score"],
                    "best_gamma_mismatch": best["gamma_mismatch"],
                    "best_total_mismatch": best["total_mismatch"],
                    "best_prior_penalty": best["prior_penalty"],
                    "best_gamma_ratio_summary": best["predicted_gamma_ratio_summary"],
                    "best_total_ratio_summary": best["predicted_total_ratio_summary"],
                    "observed_gamma_ratio_summary": best["observed_gamma_ratio_summary"],
                    "observed_total_ratio_summary": best["observed_total_ratio_summary"],
                    "alpha1": best["alpha1"],
                    "alpha2": best["alpha2"],
                    "alpha3": best["alpha3"],
                    "top3": " || ".join(
                        f"{row['multipoles_compact']} [score={row['combined_score']:.3f}, basis={row['comparison_basis']}]"
                        for _, row in top3.iterrows()
                    ),
                    "same_initial_level": meta["same_initial_level"],
                }
            )
    return pd.DataFrame(rows_out).sort_values(["from_keV", "best_combined_score", "E1_keV", "E2_keV", "E3_keV"]) if rows_out else pd.DataFrame()


def build_level_group_summary(df: pd.DataFrame, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    out = []
    for from_energy, grp in df.groupby("from (keV)", dropna=True):
        grp = grp.copy()
        grp = grp[pd.to_numeric(grp[intensity_col], errors="coerce") > 0]
        if len(grp) < 2:
            continue
        energies = sorted(float(x) for x in grp[e_col].tolist())
        intensities = [float(x) for x in grp[intensity_col].tolist()]
        out.append(
            {
                "from_keV": float(from_energy),
                "n_transitions": len(grp),
                "energies_keV": ", ".join(f"{x:.2f}" for x in energies),
                "intensity_sum": float(np.sum(intensities)),
                "max_intensity": float(np.max(intensities)),
                "min_intensity": float(np.min(intensities)),
            }
        )
    return pd.DataFrame(out).sort_values(["n_transitions", "from_keV"], ascending=[False, True]) if out else pd.DataFrame()


def build_transition_table(df: pd.DataFrame, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    rows = []
    for idx, row in df.iterrows():
        allowed = allowed_multipoles(row.get("from spin"), row.get("onto spin"), max_L=5)
        alpha_allowed = [icc_value(row, m) for m in allowed if m in row.index]
        rows.append(
            {
                "row_index": idx,
                "E_keV": float(row[e_col]),
                "from_keV": float(row.get("from (keV)", np.nan)),
                "to_keV": float(row.get("onto (keV)", np.nan)),
                "from_spin_raw": row.get("from spin", ""),
                "onto_spin_raw": row.get("onto spin", ""),
                "from_spin_parsed": spin_label(row.get("from spin")),
                "onto_spin_parsed": spin_label(row.get("onto spin")),
                "intensity": float(row.get(intensity_col, np.nan)),
                "allowed_multipoles": ", ".join(allowed),
                "alpha_min_allowed": min(alpha_allowed) if alpha_allowed else np.nan,
                "alpha_max_allowed": max(alpha_allowed) if alpha_allowed else np.nan,
                "icc_hint": icc_recommendation(max(alpha_allowed) if alpha_allowed else 0.0),
            }
        )
    return pd.DataFrame(rows).sort_values("E_keV")


def export_batch(df: pd.DataFrame, out_dir: str, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> Tuple[str, str, str, str]:
    os.makedirs(out_dir, exist_ok=True)
    pairs = build_pairwise_dataset(df, A=A, intensity_col=intensity_col)
    triplets = build_triplet_dataset(df, A=A, intensity_col=intensity_col)
    transitions = build_transition_table(df, intensity_col=intensity_col)
    levels = build_level_group_summary(df, intensity_col=intensity_col)

    pairs_path = os.path.join(out_dir, "pairwise_branching_analysis.csv")
    triplets_path = os.path.join(out_dir, "triplet_branching_analysis.csv")
    trans_path = os.path.join(out_dir, "transition_selection_rules_summary.csv")
    levels_path = os.path.join(out_dir, "level_group_summary.csv")

    pairs.to_csv(pairs_path, index=False)
    triplets.to_csv(triplets_path, index=False)
    transitions.to_csv(trans_path, index=False)
    levels.to_csv(levels_path, index=False)
    return pairs_path, triplets_path, trans_path, levels_path


def format_transition_line(label: str, transition: Dict[str, object]) -> str:
    return (
        f"{label}: {transition['E_keV']:.2f} keV | {transition['from_keV']:.2f} -> {transition['to_keV']:.2f} keV | "
        f"spiny: {transition['from_spin_parsed']} -> {transition['onto_spin_parsed']} | dozwolone: {transition['allowed_multipoles']}"
    )


def pretty_top3_blocks(ranked: pd.DataFrame) -> List[str]:
    blocks = []
    for idx, (_, row) in enumerate(ranked.head(TOP_N).iterrows(), start=1):
        parts = [
            f"{idx}. {row['multipoles_compact']}",
            f"   score łączny: {row['combined_score']:.4f}",
            f"   baza porównania: {row['comparison_basis']}",
            f"   mismatch efektywny: {row['effective_mismatch']:.4f}",
            f"   mismatch gamma: {row['gamma_mismatch']:.4f}",
            f"   mismatch total: {row['total_mismatch']:.4f}",
            f"   kara za wyższe multipolowości: {row['prior_penalty']:.4f}",
            f"   kara za duże zmiany spinu: {row['spin_penalty']:.4f}",
            f"   preferowany stan początkowy: {row['preferred_initial_spin'] if pd.notna(row['preferred_initial_spin']) else 'brak'}{row['preferred_initial_parity'] if pd.notna(row['preferred_initial_parity']) else ''}",
            f"   max ΔJ dla preferowanego spinu: {row['preferred_initial_spin_max_dJ'] if pd.notna(row['preferred_initial_spin_max_dJ']) else 'brak'}",
            f"   średnie ΔJ dla preferowanego spinu: {row['preferred_initial_spin_mean_dJ'] if pd.notna(row['preferred_initial_spin_mean_dJ']) else 'brak'}",
            f"   alpha: {row['alpha_summary']}",
            "   stosunki intensywności i prawdopodobieństw:",
        ]
        parts.extend(row_ratio_lines(row, int(row['n_transitions'])))
        blocks.append("\n".join(parts))
    return blocks


def pretty_compare_report(meta: Dict[str, object], ranked: pd.DataFrame) -> str:
    title = f"=== PORÓWNANIE {meta['n_transitions']} PRZEJŚĆ ==="
    lines = [title]
    for i, transition in enumerate(meta["transitions"], start=1):
        lines.append(format_transition_line(f"Przejście {i}", transition))
    fracs = ", ".join(f"{x:.4f}" for x in meta["observed_gamma_fractions"])
    lines.append(f"Znormalizowane udziały obserwowanych intensywności gamma: {fracs}")
    lines.append(f"Stosunki obserwowanych intensywności gamma: {meta['observed_gamma_ratio_summary']}")
    if meta.get("zero_plus_constraint_active"):
        lines.append("Aktywne ograniczenie 0+: gdy co najmniej dwa porównywane przejścia zaczynają się w 0+ lub kończą w 0+, program dopuszcza tylko identyczne multipolowości, np. E1/E1 lub M2/M2.")
    lines.append(meta["note"])
    lines.append("")
    lines.append("TOP 3 najbardziej prawdopodobne możliwości, z preferencją niższych multipolowości:")
    lines.extend(pretty_top3_blocks(ranked))
    lines.append("")
    preview_cols = ["multipoles_compact", "combined_score", "comparison_basis", "effective_mismatch", "prior_penalty", "spin_penalty", "preferred_initial_spin", "preferred_initial_parity", "max_alpha", "observed_gamma_ratio_summary", "predicted_gamma_ratio_summary"]
    lines.append("Skrócona tabela wyników:")
    lines.append(ranked.head(10)[preview_cols].to_string(index=False))
    return "\n".join(lines)


class WeisskopfApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Weisskopf branching helper")
        self.df: Optional[pd.DataFrame] = None

        self.path_var = tk.StringVar(value=DEFAULT_PATH)
        self.A_var = tk.StringVar(value="122")
        self.intensity_var = tk.StringVar(value=DEFAULT_INTENSITY_COL)
        self.tol_var = tk.StringVar(value="0.4")
        self.e1_var = tk.StringVar(value="650.05")
        self.e2_var = tk.StringVar(value="689.30")
        self.e3_var = tk.StringVar(value="821.19")
        self.out_var = tk.StringVar(value="./weisskopf_outputs")

        self._configure_fonts()
        self._build_ui()

    def _configure_fonts(self):
        if tkfont is None:
            return
        base = tkfont.nametofont("TkDefaultFont")
        base.configure(size=13)
        tkfont.nametofont("TkTextFont").configure(size=13)
        tkfont.nametofont("TkMenuFont").configure(size=13)
        tkfont.nametofont("TkHeadingFont").configure(size=14, weight="bold")
        tkfont.nametofont("TkCaptionFont").configure(size=12)
        self.mono_font = ("Courier New", 13)

    def _build_ui(self):
        self.root.geometry("1550x980")
        frame = ttk.Frame(self.root, padding=14)
        frame.grid(sticky="nsew")
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)

        for col in range(4):
            frame.columnconfigure(col, weight=1 if col == 1 else 0)
        frame.rowconfigure(10, weight=1)

        ttk.Label(frame, text="CSV:").grid(row=0, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.path_var, width=90).grid(row=0, column=1, sticky="ew", pady=4)
        ttk.Button(frame, text="Wybierz plik", command=self.choose_file).grid(row=0, column=2, padx=6, pady=4)
        ttk.Button(frame, text="Wczytaj", command=self.load_file).grid(row=0, column=3, padx=6, pady=4)

        ttk.Label(frame, text="A:").grid(row=1, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.A_var, width=14).grid(row=1, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Kolumna intensywności:").grid(row=2, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.intensity_var, width=24).grid(row=2, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Tolerancja energii [keV]:").grid(row=3, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.tol_var, width=14).grid(row=3, column=1, sticky="w", pady=4)

        ttk.Label(frame, text="Energia 1 [keV]:").grid(row=4, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.e1_var, width=18).grid(row=4, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Energia 2 [keV]:").grid(row=5, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.e2_var, width=18).grid(row=5, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Energia 3 [keV]:").grid(row=6, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.e3_var, width=18).grid(row=6, column=1, sticky="w", pady=4)

        btn_frame = ttk.Frame(frame)
        btn_frame.grid(row=7, column=0, columnspan=4, sticky="w", pady=(10, 8))
        ttk.Button(btn_frame, text="Porównaj 2 gammy", command=self.run_compare_2).grid(row=0, column=0, padx=(0, 8))
        ttk.Button(btn_frame, text="Porównaj 3 gammy", command=self.run_compare_3).grid(row=0, column=1, padx=8)
        ttk.Button(btn_frame, text="Przeskanuj cały plik", command=self.run_scan_all).grid(row=0, column=2, padx=8)

        ttk.Label(frame, text="Folder wyjściowy:").grid(row=8, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.out_var, width=60).grid(row=8, column=1, sticky="ew", pady=4)
        ttk.Button(frame, text="Eksport CSV", command=self.run_export).grid(row=8, column=2, padx=6, pady=4)

        top3_frame = ttk.LabelFrame(frame, text="Top 3 możliwości")
        top3_frame.grid(row=9, column=0, columnspan=4, sticky="ew", pady=(10, 10))
        for col in range(3):
            top3_frame.columnconfigure(col, weight=1)
        self.top_boxes = []
        for col in range(3):
            txt = tk.Text(top3_frame, wrap="word", height=9, width=45, font=self.mono_font, bg="#fbfbfb")
            txt.grid(row=0, column=col, sticky="nsew", padx=6, pady=6)
            self.top_boxes.append(txt)

        self.text = tk.Text(frame, wrap="word", width=150, height=28, font=self.mono_font)
        self.text.grid(row=10, column=0, columnspan=4, sticky="nsew", pady=(6, 0))
        self.text.insert("end", "Wczytaj CSV, a potem wpisz dwie albo trzy energie gamma do porównania.\n")

    def choose_file(self):
        if filedialog is None:
            return
        path = filedialog.askopenfilename(filetypes=[("CSV", "*.csv"), ("All files", "*.*")])
        if path:
            self.path_var.set(path)

    def load_file(self):
        try:
            self.df = load_dataframe(self.path_var.get())
            self.clear_outputs()
            self.text.insert("end", f"Wczytano {len(self.df)} wierszy.\nKolumny:\n")
            self.text.insert("end", ", ".join(self.df.columns) + "\n")
        except Exception as exc:
            self._show_error(exc)

    def clear_outputs(self):
        self.text.delete("1.0", "end")
        for box in self.top_boxes:
            box.delete("1.0", "end")

    def _require_df(self):
        if self.df is None:
            self.load_file()
        if self.df is None:
            raise ValueError("Najpierw wczytaj plik CSV.")
        return self.df

    def _show_error(self, exc: Exception):
        if messagebox:
            messagebox.showerror("Błąd", str(exc))
        else:
            raise exc

    def _render_top3(self, ranked: pd.DataFrame):
        blocks = pretty_top3_blocks(ranked)
        for idx, box in enumerate(self.top_boxes):
            box.delete("1.0", "end")
            if idx < len(blocks):
                box.insert("end", blocks[idx])

    def run_compare_2(self):
        try:
            df = self._require_df()
            meta, ranked = compare_two_transitions(
                df,
                float(self.e1_var.get()),
                float(self.e2_var.get()),
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
                tol_keV=float(self.tol_var.get()),
            )
            self.clear_outputs()
            self._render_top3(ranked)
            self.text.insert("end", pretty_compare_report(meta, ranked))
        except Exception as exc:
            self._show_error(exc)

    def run_compare_3(self):
        try:
            df = self._require_df()
            meta, ranked = compare_three_transitions(
                df,
                float(self.e1_var.get()),
                float(self.e2_var.get()),
                float(self.e3_var.get()),
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
                tol_keV=float(self.tol_var.get()),
            )
            self.clear_outputs()
            self._render_top3(ranked)
            self.text.insert("end", pretty_compare_report(meta, ranked))
        except Exception as exc:
            self._show_error(exc)

    def run_scan_all(self):
        try:
            df = self._require_df()
            pairs = build_pairwise_dataset(df, A=float(self.A_var.get()), intensity_col=self.intensity_var.get())
            triplets = build_triplet_dataset(df, A=float(self.A_var.get()), intensity_col=self.intensity_var.get())
            levels = build_level_group_summary(df, intensity_col=self.intensity_var.get())
            self.clear_outputs()
            self.text.insert("end", "=== SKAN CAŁEGO PLIKU ===\n")
            self.text.insert("end", f"Poziomy z co najmniej 2 gamma: {len(levels)}\n")
            self.text.insert("end", f"Zestawy par: {len(pairs)}\n")
            self.text.insert("end", f"Zestawy trójek: {len(triplets)}\n\n")
            self.text.insert("end", "Najciekawsze pary:\n")
            if not pairs.empty:
                cols = ["from_keV", "E1_keV", "E2_keV", "best_multipole_1", "best_multipole_2", "best_combined_score", "top3"]
                self.text.insert("end", pairs.head(10)[cols].to_string(index=False))
            else:
                self.text.insert("end", "Brak par do analizy.\n")
            self.text.insert("end", "\n\nNajciekawsze trójki:\n")
            if not triplets.empty:
                cols = ["from_keV", "E1_keV", "E2_keV", "E3_keV", "best_m1", "best_m2", "best_m3", "best_combined_score", "top3"]
                self.text.insert("end", triplets.head(10)[cols].to_string(index=False))
            else:
                self.text.insert("end", "Brak trójek do analizy.\n")
            if not pairs.empty:
                top_candidates = []
                for _, row in pairs.head(TOP_N).iterrows():
                    top_candidates.append(
                        "\n".join(
                            [
                                f"Poziom {row['from_keV']:.2f} keV",
                                f"E: {row['E1_keV']:.2f} i {row['E2_keV']:.2f} keV",
                                f"najlepsze: {row['best_multipole_1']}/{row['best_multipole_2']}",
                                f"score: {row['best_combined_score']:.4f}",
                                row['top3'],
                            ]
                        )
                    )
                for idx, box in enumerate(self.top_boxes):
                    box.delete("1.0", "end")
                    if idx < len(top_candidates):
                        box.insert("end", top_candidates[idx])
        except Exception as exc:
            self._show_error(exc)

    def run_export(self):
        try:
            df = self._require_df()
            pairs_path, triplets_path, trans_path, levels_path = export_batch(
                df,
                self.out_var.get(),
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
            )
            self.text.insert(
                "end",
                f"\n\nZapisano:\n- {pairs_path}\n- {triplets_path}\n- {trans_path}\n- {levels_path}\n",
            )
            if messagebox:
                messagebox.showinfo(
                    "Gotowe",
                    f"Zapisano pliki:\n{pairs_path}\n{triplets_path}\n{trans_path}\n{levels_path}",
                )
        except Exception as exc:
            self._show_error(exc)


def main():
    parser = argparse.ArgumentParser(description="Weisskopf helper do analizy branchingu i reguł wyboru")
    parser.add_argument("--csv", default=DEFAULT_PATH)
    parser.add_argument("--A", type=float, default=122)
    parser.add_argument("--intensity-col", default=DEFAULT_INTENSITY_COL)
    parser.add_argument("--compare", nargs=2, type=float, metavar=("E1", "E2"))
    parser.add_argument("--compare3", nargs=3, type=float, metavar=("E1", "E2", "E3"))
    parser.add_argument("--tol", type=float, default=0.4)
    parser.add_argument("--export-dir")
    parser.add_argument("--scan-all", action="store_true")
    parser.add_argument("--no-gui", action="store_true")
    args = parser.parse_args()

    if args.compare or args.compare3 or args.export_dir or args.scan_all or args.no_gui or tk is None:
        df = load_dataframe(args.csv)
        if args.compare:
            meta, ranked = compare_two_transitions(
                df,
                args.compare[0],
                args.compare[1],
                A=args.A,
                intensity_col=args.intensity_col,
                tol_keV=args.tol,
            )
            print(pretty_compare_report(meta, ranked))
        if args.compare3:
            meta, ranked = compare_three_transitions(
                df,
                args.compare3[0],
                args.compare3[1],
                args.compare3[2],
                A=args.A,
                intensity_col=args.intensity_col,
                tol_keV=args.tol,
            )
            print(pretty_compare_report(meta, ranked))
        if args.scan_all:
            pairs = build_pairwise_dataset(df, A=args.A, intensity_col=args.intensity_col)
            triplets = build_triplet_dataset(df, A=args.A, intensity_col=args.intensity_col)
            levels = build_level_group_summary(df, intensity_col=args.intensity_col)
            print(f"Poziomy z co najmniej 2 gamma: {len(levels)}")
            print(f"Zestawy par: {len(pairs)}")
            print(f"Zestawy trójek: {len(triplets)}")
            if not pairs.empty:
                print("\nTop pary:")
                print(pairs.head(10).to_string(index=False))
            if not triplets.empty:
                print("\nTop trójki:")
                print(triplets.head(10).to_string(index=False))
        if args.export_dir:
            paths = export_batch(df, args.export_dir, A=args.A, intensity_col=args.intensity_col)
            print("Zapisano:")
            for path in paths:
                print(f"- {path}")
        return

    root = tk.Tk()
    WeisskopfApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
