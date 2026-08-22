from __future__ import annotations

import argparse

from .analysis import compare_two_transitions, scan_table
from .data import load_csv


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Analyze gamma-transition multipolarities and Weisskopf ratios.")
    p.add_argument("csv", help="Input CSV with gamma intensities")
    p.add_argument("--energy", nargs=2, type=float, metavar=("E1", "E2"), help="Compare two gamma energies in keV")
    p.add_argument("--A", type=float, default=122, help="Mass number used in Weisskopf estimates")
    p.add_argument("--intensity-col", default=None, help="Gamma intensity column, default auto-detects relative (%)")
    p.add_argument("--tolerance", type=float, default=0.5, help="Energy matching tolerance in keV")
    p.add_argument("--scan", action="store_true", help="Print a compact scan of selection-rule information")
    args = p.parse_args(argv)

    df, cmap = load_csv(args.csv, intensity_col=args.intensity_col)
    if args.energy:
        transitions, result = compare_two_transitions(df, cmap, args.energy[0], args.energy[1], A=args.A, tolerance_keV=args.tolerance)
        for t in transitions:
            print(f"E={t.energy:.2f} keV, I_gamma={t.intensity_gamma:.6g}, from={t.from_energy}, to={t.to_energy}")
            print(f"  spin pocz.: {t.from_spin_summary}; spin końc.: {t.to_spin_summary}")
        print(result.to_string(index=False))
    if args.scan or not args.energy:
        print(scan_table(df, cmap, A=args.A).to_string(index=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
