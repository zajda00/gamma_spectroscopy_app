import argparse
import itertools
import math
import os
import re
from dataclasses import dataclass
from math import factorial
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np
import pandas as pd

try:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk
except Exception:
    tk = None
    filedialog = None
    messagebox = None
    ttk = None

ALL_MULTIPOLES = [f"{kind}{L}" for L in range(1, 6) for kind in ("E", "M")]
DEFAULT_INTENSITY_COL = "relative (%)"
DEFAULT_PATH = "AI_processing_2_v2.csv"


@dataclass(frozen=True)
class JPi:
    J: int
    parity: Optional[str] = None

    def label(self) -> str:
        return f"{self.J}{self.parity or '?'}"


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
            J = int(m.group(1))
            parity = m.group(2) or shared_sign
            cand = JPi(J, parity)
            if cand not in out:
                out.append(cand)

    return out


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


def weisskopf_B(multipole: str, A: float) -> float:
    kind = multipole[0]
    L = int(multipole[1:])
    if kind == "E":
        return (1.2 ** (2 * L) / (4 * math.pi)) * ((3 / (L + 3)) ** 2) * (A ** (2 * L / 3))
    return (10 / math.pi) * (1.2 ** (2 * L - 2)) * ((3 / (L + 3)) ** 2) * (A ** ((2 * L - 2) / 3))


def lambda_wu(multipole: str, E_keV: float, A: float) -> float:
    E_MeV = float(E_keV) / 1000.0
    kind = multipole[0]
    L = int(multipole[1:])
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
    diff = (df[e_col].astype(float) - float(energy_keV)).abs()
    idx = diff.idxmin()
    if float(diff.loc[idx]) > tol_keV:
        raise ValueError(f"Nie znaleziono przejścia w pobliżu {energy_keV} keV, najbliższe ma odchyłkę {diff.loc[idx]:.3f} keV")
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


def spin_label(value) -> str:
    cands = parse_spin_candidates(value)
    if not cands:
        return "brak / niepewne"
    return ", ".join(c.label() for c in cands)


def transition_summary(row: pd.Series, A: float, intensity_col: str = DEFAULT_INTENSITY_COL) -> Dict[str, object]:
    allowed = allowed_multipoles(row.get("from spin"), row.get("onto spin"), max_L=5)
    return {
        "E_keV": float(row[find_energy_column(pd.DataFrame([row]))]),
        "from_keV": float(row.get("from (keV)", np.nan)),
        "to_keV": float(row.get("onto (keV)", np.nan)),
        "from_spin_raw": row.get("from spin", ""),
        "onto_spin_raw": row.get("onto spin", ""),
        "from_spin_parsed": spin_label(row.get("from spin")),
        "onto_spin_parsed": spin_label(row.get("onto spin")),
        "allowed_multipoles": ", ".join(allowed),
        "intensity": float(row.get(intensity_col, np.nan)),
    }


def compare_two_transitions(
    df: pd.DataFrame,
    energy_1: float,
    energy_2: float,
    A: float = 122,
    intensity_col: str = DEFAULT_INTENSITY_COL,
    tol_keV: float = 0.4,
) -> Tuple[Dict[str, object], pd.DataFrame]:
    row1 = find_transition(df, energy_1, tol_keV)
    row2 = find_transition(df, energy_2, tol_keV)

    e_col = find_energy_column(df)
    I1 = float(row1[intensity_col])
    I2 = float(row2[intensity_col])
    if I1 <= 0 or I2 <= 0:
        raise ValueError("Obie intensywności muszą być dodatnie.")

    allowed1 = allowed_multipoles(row1.get("from spin"), row1.get("onto spin"), max_L=5)
    allowed2 = allowed_multipoles(row2.get("from spin"), row2.get("onto spin"), max_L=5)

    obs_ratio_gamma = I1 / I2
    same_initial = math.isclose(float(row1.get("from (keV)", np.nan)), float(row2.get("from (keV)", np.nan)), abs_tol=1e-6)

    results = []
    for m1, m2 in itertools.product(allowed1, allowed2):
        lam1 = lambda_wu(m1, float(row1[e_col]), A)
        lam2 = lambda_wu(m2, float(row2[e_col]), A)
        pred_ratio_gamma = lam1 / lam2
        alpha1 = icc_value(row1, m1)
        alpha2 = icc_value(row2, m2)
        obs_ratio_total = (I1 * (1 + alpha1)) / (I2 * (1 + alpha2))
        pred_ratio_total = (lam1 * (1 + alpha1)) / (lam2 * (1 + alpha2))
        score_gamma = abs(math.log10(pred_ratio_gamma / obs_ratio_gamma))
        score_total = abs(math.log10(pred_ratio_total / obs_ratio_total))
        results.append(
            {
                "E1_keV": float(row1[e_col]),
                "E2_keV": float(row2[e_col]),
                "multipole_1": m1,
                "multipole_2": m2,
                "lambda1_WU": lam1,
                "lambda2_WU": lam2,
                "pred_ratio_gamma": pred_ratio_gamma,
                "obs_ratio_gamma": obs_ratio_gamma,
                "score_gamma_log10": score_gamma,
                "alpha1": alpha1,
                "alpha2": alpha2,
                "pred_ratio_total": pred_ratio_total,
                "obs_ratio_total": obs_ratio_total,
                "score_total_log10": score_total,
                "from1_keV": float(row1.get("from (keV)", np.nan)),
                "from2_keV": float(row2.get("from (keV)", np.nan)),
                "same_initial_level": same_initial,
            }
        )

    res = pd.DataFrame(results).sort_values(["score_gamma_log10", "score_total_log10", "multipole_1", "multipole_2"]).reset_index(drop=True)

    meta = {
        "transition_1": transition_summary(row1, A, intensity_col),
        "transition_2": transition_summary(row2, A, intensity_col),
        "same_initial_level": same_initial,
        "observed_gamma_ratio": obs_ratio_gamma,
        "note": (
            "Porównanie jest fizycznie sensowne jako stosunek branchingu tylko wtedy, gdy oba przejścia wychodzą z tego samego poziomu początkowego."
            if same_initial
            else "Uwaga: przejścia wychodzą z różnych poziomów, więc surowy stosunek intensywności miesza branching z zasilaniem poziomów. To może służyć tylko jako bardzo zgrubna heurystyka."
        ),
    }
    return meta, res


def build_pairwise_dataset(df: pd.DataFrame, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    rows = []
    for from_energy, grp in df.groupby("from (keV)", dropna=True):
        grp = grp.copy()
        grp = grp[pd.to_numeric(grp[intensity_col], errors="coerce") > 0]
        if len(grp) < 2:
            continue
        indices = list(grp.index)
        for i, j in itertools.combinations(indices, 2):
            row_i = df.loc[i]
            row_j = df.loc[j]
            meta, ranked = compare_two_transitions(df, float(row_i[e_col]), float(row_j[e_col]), A=A, intensity_col=intensity_col)
            best = ranked.iloc[0].to_dict()
            rows.append(
                {
                    "from_keV": float(from_energy),
                    "E1_keV": float(row_i[e_col]),
                    "E2_keV": float(row_j[e_col]),
                    "I1": float(row_i[intensity_col]),
                    "I2": float(row_j[intensity_col]),
                    "obs_ratio_gamma": meta["observed_gamma_ratio"],
                    "best_multipole_1": best["multipole_1"],
                    "best_multipole_2": best["multipole_2"],
                    "score_gamma_log10": best["score_gamma_log10"],
                    "alpha1": best["alpha1"],
                    "alpha2": best["alpha2"],
                    "icc1_recommendation": icc_recommendation(best["alpha1"]),
                    "icc2_recommendation": icc_recommendation(best["alpha2"]),
                    "from_spin_1": row_i.get("from spin", ""),
                    "onto_spin_1": row_i.get("onto spin", ""),
                    "from_spin_2": row_j.get("from spin", ""),
                    "onto_spin_2": row_j.get("onto spin", ""),
                    "allowed_1": ", ".join(allowed_multipoles(row_i.get("from spin"), row_i.get("onto spin"))),
                    "allowed_2": ", ".join(allowed_multipoles(row_j.get("from spin"), row_j.get("onto spin"))),
                }
            )
    return pd.DataFrame(rows).sort_values(["from_keV", "score_gamma_log10", "E1_keV", "E2_keV"]) if rows else pd.DataFrame()


def build_transition_table(df: pd.DataFrame, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> pd.DataFrame:
    e_col = find_energy_column(df)
    rows = []
    for _, row in df.iterrows():
        allowed = allowed_multipoles(row.get("from spin"), row.get("onto spin"), max_L=5)
        alpha_allowed = [icc_value(row, m) for m in allowed if m in row.index]
        rows.append(
            {
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
            }
        )
    return pd.DataFrame(rows).sort_values("E_keV")


def export_batch(df: pd.DataFrame, out_dir: str, A: float = 122, intensity_col: str = DEFAULT_INTENSITY_COL) -> Tuple[str, str]:
    os.makedirs(out_dir, exist_ok=True)
    pairs = build_pairwise_dataset(df, A=A, intensity_col=intensity_col)
    transitions = build_transition_table(df, A=A, intensity_col=intensity_col)
    pairs_path = os.path.join(out_dir, "pairwise_branching_analysis.csv")
    trans_path = os.path.join(out_dir, "transition_selection_rules_summary.csv")
    pairs.to_csv(pairs_path, index=False)
    transitions.to_csv(trans_path, index=False)
    return pairs_path, trans_path


def pretty_compare_report(meta: Dict[str, object], ranked: pd.DataFrame, top_n: int = 10) -> str:
    t1 = meta["transition_1"]
    t2 = meta["transition_2"]
    lines = []
    lines.append("=== PORÓWNANIE DWÓCH PRZEJŚĆ ===")
    lines.append(f"Przejście 1: {t1['E_keV']:.2f} keV | {t1['from_keV']:.2f} -> {t1['to_keV']:.2f} keV | spiny: {t1['from_spin_parsed']} -> {t1['onto_spin_parsed']}")
    lines.append(f"Przejście 2: {t2['E_keV']:.2f} keV | {t2['from_keV']:.2f} -> {t2['to_keV']:.2f} keV | spiny: {t2['from_spin_parsed']} -> {t2['onto_spin_parsed']}")
    lines.append(f"Stosunek obserwowanych intensywności gamma: {meta['observed_gamma_ratio']:.6g}")
    lines.append(meta['note'])
    lines.append("")
    lines.append("Najlepsze dopasowania Weisskopfa:")
    preview = ranked.head(top_n).copy()
    keep = [
        "multipole_1", "multipole_2", "pred_ratio_gamma", "obs_ratio_gamma", "score_gamma_log10",
        "alpha1", "alpha2", "pred_ratio_total", "obs_ratio_total", "score_total_log10"
    ]
    lines.append(preview[keep].to_string(index=False))
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
        self.out_var = tk.StringVar(value="./weisskopf_outputs")

        self._build_ui()

    def _build_ui(self):
        frame = ttk.Frame(self.root, padding=10)
        frame.grid(sticky="nsew")
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        frame.columnconfigure(1, weight=1)

        ttk.Label(frame, text="CSV:").grid(row=0, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.path_var, width=70).grid(row=0, column=1, sticky="ew")
        ttk.Button(frame, text="Wybierz plik", command=self.choose_file).grid(row=0, column=2, padx=5)
        ttk.Button(frame, text="Wczytaj", command=self.load_file).grid(row=0, column=3, padx=5)

        ttk.Label(frame, text="A:").grid(row=1, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.A_var, width=12).grid(row=1, column=1, sticky="w")
        ttk.Label(frame, text="Kolumna intensywności:").grid(row=2, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.intensity_var, width=20).grid(row=2, column=1, sticky="w")
        ttk.Label(frame, text="Tolerancja energii [keV]:").grid(row=3, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.tol_var, width=12).grid(row=3, column=1, sticky="w")

        ttk.Label(frame, text="Energia 1 [keV]:").grid(row=4, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.e1_var, width=20).grid(row=4, column=1, sticky="w")
        ttk.Label(frame, text="Energia 2 [keV]:").grid(row=5, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.e2_var, width=20).grid(row=5, column=1, sticky="w")

        ttk.Button(frame, text="Porównaj przejścia", command=self.run_compare).grid(row=6, column=0, pady=8, sticky="w")

        ttk.Label(frame, text="Folder wyjściowy:").grid(row=7, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.out_var, width=50).grid(row=7, column=1, sticky="ew")
        ttk.Button(frame, text="Eksport partii", command=self.run_export).grid(row=7, column=2, padx=5)

        self.text = tk.Text(frame, wrap="word", width=120, height=35)
        self.text.grid(row=8, column=0, columnspan=4, sticky="nsew", pady=(10, 0))
        frame.rowconfigure(8, weight=1)

        self.text.insert("end", "Wczytaj CSV, a potem wpisz dwie energie gamma do porównania.\n")

    def choose_file(self):
        if filedialog is None:
            return
        path = filedialog.askopenfilename(filetypes=[("CSV", "*.csv"), ("All files", "*.*")])
        if path:
            self.path_var.set(path)

    def load_file(self):
        try:
            self.df = load_dataframe(self.path_var.get())
            self.text.delete("1.0", "end")
            self.text.insert("end", f"Wczytano {len(self.df)} wierszy.\nKolumny:\n")
            self.text.insert("end", ", ".join(self.df.columns) + "\n")
        except Exception as exc:
            if messagebox:
                messagebox.showerror("Błąd", str(exc))
            else:
                raise

    def _require_df(self):
        if self.df is None:
            self.load_file()
        if self.df is None:
            raise ValueError("Najpierw wczytaj plik CSV.")
        return self.df

    def run_compare(self):
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
            self.text.delete("1.0", "end")
            self.text.insert("end", pretty_compare_report(meta, ranked, top_n=12))
        except Exception as exc:
            if messagebox:
                messagebox.showerror("Błąd", str(exc))
            else:
                raise

    def run_export(self):
        try:
            df = self._require_df()
            pairs_path, trans_path = export_batch(
                df,
                self.out_var.get(),
                A=float(self.A_var.get()),
                intensity_col=self.intensity_var.get(),
            )
            self.text.insert("end", f"\n\nZapisano:\n- {pairs_path}\n- {trans_path}\n")
            if messagebox:
                messagebox.showinfo("Gotowe", f"Zapisano pliki:\n{pairs_path}\n{trans_path}")
        except Exception as exc:
            if messagebox:
                messagebox.showerror("Błąd", str(exc))
            else:
                raise


def main():
    parser = argparse.ArgumentParser(description="Weisskopf helper do analizy branchingu i reguł wyboru")
    parser.add_argument("--csv", default=DEFAULT_PATH)
    parser.add_argument("--A", type=float, default=122)
    parser.add_argument("--intensity-col", default=DEFAULT_INTENSITY_COL)
    parser.add_argument("--compare", nargs=2, type=float, metavar=("E1", "E2"))
    parser.add_argument("--tol", type=float, default=0.4)
    parser.add_argument("--export-dir")
    parser.add_argument("--no-gui", action="store_true")
    args = parser.parse_args()

    if args.compare or args.export_dir or args.no_gui or tk is None:
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
            print(pretty_compare_report(meta, ranked, top_n=12))
        if args.export_dir:
            pairs_path, trans_path = export_batch(df, args.export_dir, A=args.A, intensity_col=args.intensity_col)
            print(f"Zapisano:\n- {pairs_path}\n- {trans_path}")
        return

    root = tk.Tk()
    app = WeisskopfApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
