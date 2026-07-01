import argparse
import itertools
import math
import os
import re
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

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

DEFAULT_PATH = "AI_processing_2_v2.csv"
DEFAULT_INTENSITY_COL = "relative (%)"
DEFAULT_ANCHORS = "0:0+"
MULTIPOLES = [f"{kind}{L}" for L in range(1, 6) for kind in ("E", "M")]


@dataclass(frozen=True)
class State:
    J: int
    parity: str

    def label(self) -> str:
        return f"{self.J}{self.parity}"


@dataclass
class BranchOption:
    to_level: float
    to_state: State
    multipole: str
    lambda_wu: float
    alpha: float
    delta_j: int
    intrinsic_penalty: float


def double_factorial(n: int) -> int:
    if n <= 0:
        return 1
    out = 1
    for k in range(n, 0, -2):
        out *= k
    return out


def multipole_L(multipole: str) -> int:
    return int(multipole[1:])


def multipole_kind(multipole: str) -> str:
    return multipole[0]


def parse_spin_candidates(value) -> List[State]:
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
    out: List[State] = []
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
            state = State(int(m.group(1)), m.group(2) or shared_sign or "+")
            if state not in out:
                out.append(state)
    return out


def parse_anchor_string(anchor_text: str) -> Dict[float, List[State]]:
    anchors: Dict[float, List[State]] = {}
    for chunk in anchor_text.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        if ":" not in chunk:
            raise ValueError(f"Błędny anchor: {chunk}. Użyj formatu energia:spin, np. 0:0+.")
        energy_text, spin_text = chunk.split(":", 1)
        energy = float(energy_text.strip())
        states = parse_spin_candidates(spin_text.strip())
        if not states:
            raise ValueError(f"Nie udało się odczytać spinu z anchoru: {chunk}")
        anchors[energy] = states
    if 0.0 not in anchors:
        anchors[0.0] = [State(0, "+")]
    return anchors


def parity_allows(kind: str, L: int, pi_i: str, pi_f: str) -> bool:
    same = pi_i == pi_f
    if kind == "E":
        return same if (L % 2 == 0) else (not same)
    return (not same) if (L % 2 == 0) else same


def allowed_multipoles_between_states(initial: State, final: State, max_L: int = 5) -> List[str]:
    out = []
    min_L = max(1, abs(initial.J - final.J))
    max_allowed = min(max_L, initial.J + final.J)
    for L in range(min_L, max_allowed + 1):
        if initial.J == 0 and final.J == 0:
            continue
        for kind in ("E", "M"):
            if parity_allows(kind, L, initial.parity, final.parity):
                out.append(f"{kind}{L}")
    return out


def weisskopf_B(multipole: str, A: float) -> float:
    kind = multipole_kind(multipole)
    L = multipole_L(multipole)
    if kind == "E":
        return (1.2 ** (2 * L) / (4 * math.pi)) * ((3 / (L + 3)) ** 2) * (A ** (2 * L / 3))
    return (10 / math.pi) * (1.2 ** (2 * L - 2)) * ((3 / (L + 3)) ** 2) * (A ** ((2 * L - 2) / 3))


def lambda_wu(multipole: str, E_keV: float, A: float) -> float:
    E_MeV = float(E_keV) / 1000.0
    L = multipole_L(multipole)
    pref = 5.50e22 if multipole_kind(multipole) == "E" else 6.08e20
    geom = (L + 1) / (L * (double_factorial(2 * L + 1) ** 2))
    energy = (E_MeV / 197.3) ** (2 * L + 1)
    return pref * geom * energy * weisskopf_B(multipole, A)


def normalize(values: Sequence[float]) -> np.ndarray:
    arr = np.array(values, dtype=float)
    total = arr.sum()
    if total <= 0:
        raise ValueError("Suma intensywności musi być dodatnia.")
    return arr / total


def safe_log_distance(pred: Sequence[float], obs: Sequence[float]) -> float:
    pred_arr = np.clip(np.array(pred, dtype=float), 1e-15, None)
    obs_arr = np.clip(np.array(obs, dtype=float), 1e-15, None)
    return float(np.mean(np.abs(np.log10(pred_arr / obs_arr))))


def compute_pairwise_ratios(values: Sequence[float], labels: Optional[Sequence[str]] = None) -> str:
    vals = list(map(float, values))
    parts = []
    for i, j in itertools.combinations(range(len(vals)), 2):
        label_i = labels[i] if labels else str(i + 1)
        label_j = labels[j] if labels else str(j + 1)
        parts.append(f"{label_i}/{label_j}={vals[i]/vals[j]:.6g}")
    return "; ".join(parts)


def load_dataframe(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df.columns = [c.strip() for c in df.columns]
    return df


def get_energy_column(df: pd.DataFrame) -> str:
    for c in ["E (keV)", "E_gamma_keV", "energy_keV"]:
        if c in df.columns:
            return c
    raise KeyError("Nie znaleziono kolumny energii gamma.")


def get_icc(row: pd.Series, multipole: str) -> float:
    return float(row.get(multipole, 0.0) or 0.0)


def all_candidate_states(max_J: int) -> List[State]:
    return [State(J, p) for J in range(max_J + 1) for p in ["+", "-"]]


def state_match_to_reference(state: State, ref_text: str) -> bool:
    refs = parse_spin_candidates(ref_text)
    return any(state == ref for ref in refs)


def intrinsic_option_penalty(multipole: str, delta_j: int) -> float:
    L = multipole_L(multipole)
    kind = multipole_kind(multipole)
    return 0.08 * max(0, L - 1) + 0.03 * delta_j + (0.015 if kind == "M" else 0.0)


def build_branch_options(
    row: pd.Series,
    parent_state: State,
    lower_level_candidates: List[State],
    energy_col: str,
    A: float,
    branch_option_limit: int,
) -> List[BranchOption]:
    out: List[BranchOption] = []
    to_level = float(row["onto (keV)"])
    e_gamma = float(row[energy_col])
    for lower_state in lower_level_candidates:
        for multipole in allowed_multipoles_between_states(parent_state, lower_state):
            delta_j = abs(parent_state.J - lower_state.J)
            out.append(
                BranchOption(
                    to_level=to_level,
                    to_state=lower_state,
                    multipole=multipole,
                    lambda_wu=lambda_wu(multipole, e_gamma, A),
                    alpha=get_icc(row, multipole),
                    delta_j=delta_j,
                    intrinsic_penalty=intrinsic_option_penalty(multipole, delta_j),
                )
            )
    out.sort(key=lambda x: (x.intrinsic_penalty, -x.lambda_wu, x.to_state.J, x.to_state.parity, x.multipole))
    return out[:branch_option_limit]


def choose_best_branching_combo(
    branch_rows: List[pd.Series],
    branch_options: List[List[BranchOption]],
    intensity_col: str,
) -> Tuple[float, Dict[str, object]]:
    intensities = np.array([float(row[intensity_col]) for row in branch_rows], dtype=float)
    obs_gamma_fracs = normalize(intensities)
    labels = [f"{float(row['E (keV)']):.2f}" if 'E (keV)' in row.index else f"{i+1}" for i, row in enumerate(branch_rows)]

    best_score = float("inf")
    best_meta: Dict[str, object] = {}
    weak_single_branch_penalty = 0.35 if len(branch_rows) == 1 else 0.0

    for combo in itertools.product(*branch_options):
        lambdas = np.array([opt.lambda_wu for opt in combo], dtype=float)
        alphas = np.array([opt.alpha for opt in combo], dtype=float)
        pred_gamma_fracs = normalize(lambdas)
        gamma_mismatch = safe_log_distance(pred_gamma_fracs, obs_gamma_fracs)

        observed_total = intensities * (1.0 + alphas)
        pred_total = lambdas * (1.0 + alphas)
        obs_total_fracs = normalize(observed_total)
        pred_total_fracs = normalize(pred_total)
        total_mismatch = safe_log_distance(pred_total_fracs, obs_total_fracs)

        basis = "total" if float(np.max(alphas)) >= 0.10 else "gamma"
        effective_mismatch = total_mismatch if basis == "total" else gamma_mismatch
        intrinsic = float(np.mean([opt.intrinsic_penalty for opt in combo]))
        score = effective_mismatch + intrinsic + weak_single_branch_penalty

        if score < best_score:
            best_score = score
            best_meta = {
                "basis": basis,
                "gamma_mismatch": gamma_mismatch,
                "total_mismatch": total_mismatch,
                "intrinsic_penalty": intrinsic,
                "lambdas": lambdas,
                "alphas": alphas,
                "combo": combo,
                "observed_gamma_ratio_summary": compute_pairwise_ratios(intensities, labels=[f"I({x})" for x in labels]),
                "predicted_gamma_ratio_summary": compute_pairwise_ratios(lambdas, labels=[f"W.u.({x})" for x in labels]),
                "observed_total_ratio_summary": compute_pairwise_ratios(observed_total, labels=[f"I_tot({x})" for x in labels]),
                "predicted_total_ratio_summary": compute_pairwise_ratios(pred_total, labels=[f"W.u._tot({x})" for x in labels]),
            }
    return best_score, best_meta


def infer_level_candidates(
    level_energy: float,
    branch_rows: List[pd.Series],
    level_candidate_map: Dict[float, List[Dict[str, object]]],
    energy_col: str,
    A: float,
    intensity_col: str,
    max_J: int,
    top_k: int,
    lower_candidate_limit: int = 4,
    branch_option_limit: int = 6,
) -> List[Dict[str, object]]:
    results: List[Dict[str, object]] = []
    parent_states = all_candidate_states(max_J)

    for parent_state in parent_states:
        branch_options_per_row: List[List[BranchOption]] = []
        usable_rows: List[pd.Series] = []

        for row in branch_rows:
            lower_level = float(row["onto (keV)"])
            lower_candidates_raw = level_candidate_map.get(lower_level, [])
            lower_candidates = [entry["state"] for entry in lower_candidates_raw[:lower_candidate_limit]]
            if not lower_candidates:
                continue
            options = build_branch_options(row, parent_state, lower_candidates, energy_col, A, branch_option_limit)
            if not options:
                branch_options_per_row = []
                break
            branch_options_per_row.append(options)
            usable_rows.append(row)

        if not branch_options_per_row:
            continue

        score, best_meta = choose_best_branching_combo(usable_rows, branch_options_per_row, intensity_col)
        combo = best_meta["combo"]
        results.append(
            {
                "level_keV": level_energy,
                "state": parent_state,
                "state_label": parent_state.label(),
                "score": score,
                "basis": best_meta["basis"],
                "gamma_mismatch": best_meta["gamma_mismatch"],
                "total_mismatch": best_meta["total_mismatch"],
                "intrinsic_penalty": best_meta["intrinsic_penalty"],
                "n_branches_used": len(usable_rows),
                "multipoles": "/".join(opt.multipole for opt in combo),
                "targets": "/".join(f"{opt.to_level:.2f}->{opt.to_state.label()}" for opt in combo),
                "observed_gamma_ratio_summary": best_meta["observed_gamma_ratio_summary"],
                "predicted_gamma_ratio_summary": best_meta["predicted_gamma_ratio_summary"],
                "observed_total_ratio_summary": best_meta["observed_total_ratio_summary"],
                "predicted_total_ratio_summary": best_meta["predicted_total_ratio_summary"],
                "alphas": ", ".join(f"{a:.4g}" for a in best_meta["alphas"]),
                "branch_details": " || ".join(
                    f"Eγ={float(row[energy_col]):.2f} keV, to {opt.to_level:.2f} keV as {opt.to_state.label()}, {opt.multipole}, α={opt.alpha:.4g}"
                    for row, opt in zip(usable_rows, combo)
                ),
            }
        )

    results.sort(key=lambda x: (x["score"], x["gamma_mismatch"], x["intrinsic_penalty"], x["state"].J, x["state"].parity))
    return results[:top_k]


def summarize_reference_spin(level_rows: pd.DataFrame) -> str:
    vals = []
    for v in level_rows.get("from spin", []):
        if pd.notna(v) and str(v).strip() and str(v).strip().lower() != "nan":
            vals.append(str(v).strip())
    return vals[0] if vals else ""


def infer_spins_from_data(
    df: pd.DataFrame,
    A: float = 122,
    intensity_col: str = DEFAULT_INTENSITY_COL,
    anchor_text: str = DEFAULT_ANCHORS,
    max_J: int = 8,
    top_k: int = 5,
) -> Tuple[pd.DataFrame, pd.DataFrame]:
    energy_col = get_energy_column(df)
    work = df.copy()
    work = work[pd.to_numeric(work[intensity_col], errors="coerce") > 0].copy()
    work[energy_col] = pd.to_numeric(work[energy_col], errors="coerce")
    work["from (keV)"] = pd.to_numeric(work["from (keV)"], errors="coerce")
    work["onto (keV)"] = pd.to_numeric(work["onto (keV)"], errors="coerce")

    anchors = parse_anchor_string(anchor_text)
    candidate_map: Dict[float, List[Dict[str, object]]] = {
        float(level): [
            {
                "level_keV": float(level),
                "state": state,
                "state_label": state.label(),
                "score": 0.0,
                "basis": "anchor",
                "gamma_mismatch": 0.0,
                "total_mismatch": 0.0,
                "intrinsic_penalty": 0.0,
                "n_branches_used": 0,
                "multipoles": "anchor",
                "targets": "anchor",
                "observed_gamma_ratio_summary": "",
                "predicted_gamma_ratio_summary": "",
                "observed_total_ratio_summary": "",
                "predicted_total_ratio_summary": "",
                "alphas": "",
                "branch_details": "anchor",
            }
            for state in states
        ]
        for level, states in anchors.items()
    }

    level_rows_map = {level: grp.copy() for level, grp in work.groupby("from (keV)")}
    all_levels = sorted(level_rows_map.keys())
    detailed_rows: List[Dict[str, object]] = []
    summary_rows: List[Dict[str, object]] = []

    for level in all_levels:
        if float(level) in candidate_map:
            continue
        branches = level_rows_map[level].sort_values(energy_col).to_dict("records")
        branch_series = [pd.Series(row) for row in branches]
        inferred = infer_level_candidates(
            level_energy=float(level),
            branch_rows=branch_series,
            level_candidate_map=candidate_map,
            energy_col=energy_col,
            A=A,
            intensity_col=intensity_col,
            max_J=max_J,
            top_k=top_k,
        )
        if not inferred:
            continue
        candidate_map[float(level)] = inferred

        ref_spin = summarize_reference_spin(level_rows_map[level])
        best = inferred[0]
        top3_labels = ", ".join(f"{cand['state_label']} ({cand['score']:.3f})" for cand in inferred[:3])
        summary_rows.append(
            {
                "level_keV": float(level),
                "best_inferred_spin": best["state_label"],
                "best_score": best["score"],
                "n_branches_used": best["n_branches_used"],
                "best_multipoles": best["multipoles"],
                "best_targets": best["targets"],
                "top3_inferred": top3_labels,
                "reference_spin_from_file": ref_spin,
                "best_matches_reference": state_match_to_reference(best["state"], ref_spin) if ref_spin else False,
                "any_top3_matches_reference": any(state_match_to_reference(cand["state"], ref_spin) for cand in inferred[:3]) if ref_spin else False,
                "observed_gamma_ratio_summary": best["observed_gamma_ratio_summary"],
                "predicted_gamma_ratio_summary": best["predicted_gamma_ratio_summary"],
                "observed_total_ratio_summary": best["observed_total_ratio_summary"],
                "predicted_total_ratio_summary": best["predicted_total_ratio_summary"],
                "branch_details": best["branch_details"],
            }
        )

        for rank, cand in enumerate(inferred, start=1):
            detailed_rows.append(
                {
                    "level_keV": float(level),
                    "rank": rank,
                    "inferred_spin": cand["state_label"],
                    "score": cand["score"],
                    "basis": cand["basis"],
                    "gamma_mismatch": cand["gamma_mismatch"],
                    "total_mismatch": cand["total_mismatch"],
                    "intrinsic_penalty": cand["intrinsic_penalty"],
                    "n_branches_used": cand["n_branches_used"],
                    "multipoles": cand["multipoles"],
                    "targets": cand["targets"],
                    "observed_gamma_ratio_summary": cand["observed_gamma_ratio_summary"],
                    "predicted_gamma_ratio_summary": cand["predicted_gamma_ratio_summary"],
                    "observed_total_ratio_summary": cand["observed_total_ratio_summary"],
                    "predicted_total_ratio_summary": cand["predicted_total_ratio_summary"],
                    "alphas": cand["alphas"],
                    "branch_details": cand["branch_details"],
                    "reference_spin_from_file": ref_spin,
                    "matches_reference": state_match_to_reference(cand["state"], ref_spin) if ref_spin else False,
                }
            )

    summary_df = pd.DataFrame(summary_rows).sort_values("level_keV") if summary_rows else pd.DataFrame()
    detailed_df = pd.DataFrame(detailed_rows).sort_values(["level_keV", "rank"]) if detailed_rows else pd.DataFrame()
    return summary_df, detailed_df


def export_inference(df: pd.DataFrame, out_dir: str, **kwargs) -> Tuple[str, str]:
    os.makedirs(out_dir, exist_ok=True)
    summary_df, detailed_df = infer_spins_from_data(df, **kwargs)
    summary_path = os.path.join(out_dir, "inferred_level_spins_summary.csv")
    detailed_path = os.path.join(out_dir, "inferred_level_spins_detailed.csv")
    summary_df.to_csv(summary_path, index=False)
    detailed_df.to_csv(detailed_path, index=False)
    return summary_path, detailed_path


def format_summary(summary_df: pd.DataFrame, max_rows: int = 20) -> str:
    if summary_df.empty:
        return "Brak poziomów, dla których udało się wyznaczyć kandydatów spinów."
    cols = [
        "level_keV",
        "best_inferred_spin",
        "best_score",
        "n_branches_used",
        "top3_inferred",
        "reference_spin_from_file",
        "best_matches_reference",
        "any_top3_matches_reference",
    ]
    return summary_df.head(max_rows)[cols].to_string(index=False)


class InferApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Spin inference from branching ratios")
        self.df: Optional[pd.DataFrame] = None
        self.path_var = tk.StringVar(value=DEFAULT_PATH)
        self.A_var = tk.StringVar(value="122")
        self.intensity_var = tk.StringVar(value=DEFAULT_INTENSITY_COL)
        self.anchor_var = tk.StringVar(value=DEFAULT_ANCHORS)
        self.maxj_var = tk.StringVar(value="8")
        self.topk_var = tk.StringVar(value="5")
        self.out_var = tk.StringVar(value="./weisskopf_infer_outputs")
        self._configure_fonts()
        self._build_ui()

    def _configure_fonts(self):
        if tkfont is None:
            self.mono = ("Courier New", 12)
            return
        tkfont.nametofont("TkDefaultFont").configure(size=13)
        tkfont.nametofont("TkTextFont").configure(size=13)
        tkfont.nametofont("TkHeadingFont").configure(size=14, weight="bold")
        self.mono = ("Courier New", 12)

    def _build_ui(self):
        self.root.geometry("1500x980")
        frame = ttk.Frame(self.root, padding=14)
        frame.grid(sticky="nsew")
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        frame.columnconfigure(1, weight=1)
        frame.rowconfigure(8, weight=1)

        ttk.Label(frame, text="CSV:").grid(row=0, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.path_var, width=90).grid(row=0, column=1, sticky="ew", pady=4)
        ttk.Button(frame, text="Wybierz plik", command=self.choose_file).grid(row=0, column=2, padx=6)
        ttk.Button(frame, text="Wczytaj", command=self.load_file).grid(row=0, column=3, padx=6)

        ttk.Label(frame, text="A:").grid(row=1, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.A_var, width=12).grid(row=1, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Kolumna intensywności:").grid(row=2, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.intensity_var, width=22).grid(row=2, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Anchory (np. 0:0+, 569.36:2+):").grid(row=3, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.anchor_var, width=40).grid(row=3, column=1, sticky="ew", pady=4)
        ttk.Label(frame, text="Maks. J:").grid(row=4, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.maxj_var, width=12).grid(row=4, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Top kandydatów / poziom:").grid(row=5, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.topk_var, width=12).grid(row=5, column=1, sticky="w", pady=4)
        ttk.Label(frame, text="Folder wyjściowy:").grid(row=6, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.out_var, width=50).grid(row=6, column=1, sticky="ew", pady=4)

        btns = ttk.Frame(frame)
        btns.grid(row=7, column=0, columnspan=4, sticky="w", pady=8)
        ttk.Button(btns, text="Uruchom inferencję", command=self.run_inference).grid(row=0, column=0, padx=(0, 8))
        ttk.Button(btns, text="Eksport CSV", command=self.export_csv).grid(row=0, column=1, padx=8)

        self.text = tk.Text(frame, wrap="word", font=self.mono)
        self.text.grid(row=8, column=0, columnspan=4, sticky="nsew")
        self.text.insert("end", "Ta wersja ignoruje spiny z pliku podczas inferencji i używa ich tylko do porównania końcowego.\n")

    def choose_file(self):
        if filedialog is None:
            return
        path = filedialog.askopenfilename(filetypes=[("CSV", "*.csv"), ("All files", "*.*")])
        if path:
            self.path_var.set(path)

    def _show_error(self, exc: Exception):
        if messagebox:
            messagebox.showerror("Błąd", str(exc))
        else:
            raise exc

    def load_file(self):
        try:
            self.df = load_dataframe(self.path_var.get())
            self.text.delete("1.0", "end")
            self.text.insert("end", f"Wczytano {len(self.df)} wierszy.\nKolumny:\n{', '.join(self.df.columns)}\n")
        except Exception as exc:
            self._show_error(exc)

    def _require_df(self):
        if self.df is None:
            self.load_file()
        if self.df is None:
            raise ValueError("Najpierw wczytaj plik CSV.")
        return self.df

    def run_inference(self):
        try:
            df = self._require_df()
            summary_df, detailed_df = infer_spins_from_data(
                df,
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
                anchor_text=self.anchor_var.get(),
                max_J=int(self.maxj_var.get()),
                top_k=int(self.topk_var.get()),
            )
            self.text.delete("1.0", "end")
            self.text.insert("end", "=== PODSUMOWANIE INFERENCJI SPINÓW ===\n")
            self.text.insert("end", format_summary(summary_df) + "\n\n")
            if not detailed_df.empty:
                self.text.insert("end", "=== PRZYKŁADOWE SZCZEGÓŁY ===\n")
                cols = [
                    "level_keV",
                    "rank",
                    "inferred_spin",
                    "score",
                    "multipoles",
                    "targets",
                    "predicted_gamma_ratio_summary",
                    "reference_spin_from_file",
                    "matches_reference",
                ]
                self.text.insert("end", detailed_df.head(20)[cols].to_string(index=False))
        except Exception as exc:
            self._show_error(exc)

    def export_csv(self):
        try:
            df = self._require_df()
            summary_path, detailed_path = export_inference(
                df,
                self.out_var.get(),
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
                anchor_text=self.anchor_var.get(),
                max_J=int(self.maxj_var.get()),
                top_k=int(self.topk_var.get()),
            )
            self.text.insert("end", f"\n\nZapisano:\n- {summary_path}\n- {detailed_path}\n")
            if messagebox:
                messagebox.showinfo("Gotowe", f"Zapisano:\n{summary_path}\n{detailed_path}")
        except Exception as exc:
            self._show_error(exc)


def main():
    parser = argparse.ArgumentParser(description="Alternatywna wersja, inferencja spinów bez używania spinów z pliku w samym dopasowaniu")
    parser.add_argument("--csv", default=DEFAULT_PATH)
    parser.add_argument("--A", type=float, default=122)
    parser.add_argument("--intensity-col", default=DEFAULT_INTENSITY_COL)
    parser.add_argument("--anchors", default=DEFAULT_ANCHORS)
    parser.add_argument("--max-j", type=int, default=8)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--export-dir")
    parser.add_argument("--no-gui", action="store_true")
    args = parser.parse_args()

    if args.no_gui or args.export_dir or tk is None:
        df = load_dataframe(args.csv)
        summary_df, detailed_df = infer_spins_from_data(
            df,
            A=args.A,
            intensity_col=args.intensity_col,
            anchor_text=args.anchors,
            max_J=args.max_j,
            top_k=args.top_k,
        )
        print(format_summary(summary_df, max_rows=40))
        if not detailed_df.empty:
            print("\n=== SZCZEGÓŁY ===")
            cols = [
                "level_keV",
                "rank",
                "inferred_spin",
                "score",
                "multipoles",
                "targets",
                "predicted_gamma_ratio_summary",
                "reference_spin_from_file",
                "matches_reference",
            ]
            print(detailed_df.head(40)[cols].to_string(index=False))
        if args.export_dir:
            summary_path, detailed_path = export_inference(
                df,
                args.export_dir,
                A=args.A,
                intensity_col=args.intensity_col,
                anchor_text=args.anchors,
                max_J=args.max_j,
                top_k=args.top_k,
            )
            print(f"\nZapisano:\n- {summary_path}\n- {detailed_path}")
        return

    root = tk.Tk()
    InferApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
