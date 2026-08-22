from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import pandas as pd


def safe_filename(sheet_name: str) -> str:
    """
    Zachowuje nazwę arkusza, ale zamienia znaki niedozwolone w nazwach plików
    Windows na podkreślenie.
    """
    cleaned = re.sub(r'[<>:"/\\\\|?*]', "_", sheet_name).strip()

    if not cleaned:
        cleaned = "unnamed_sheet"

    return cleaned


def get_engine(source: Path) -> str:
    suffix = source.suffix.lower()

    if suffix == ".ods":
        return "odf"

    if suffix in {".xlsx", ".xlsm"}:
        return "openpyxl"

    raise ValueError("Obsługiwane formaty: .ods, .xlsx, .xlsm")


def export_workbook(source: Path, output_dir: Path | None = None, verbose: bool = True) -> Path:
    """Export all sheets from ODS/XLSX file to individual CSV files.
    
    Args:
        source: Path to ODS or XLSX file
        output_dir: Optional output directory path. If None, creates folder with source stem name in source parent dir.
        verbose: If True, print status messages. If False, run silently.
    
    Returns:
        Path to the directory containing exported CSV files.
    
    Raises:
        FileNotFoundError: If source file doesn't exist
        ValueError: If file format is not supported
    """
    source = source.resolve()

    if not source.exists():
        raise FileNotFoundError(f"Nie znaleziono pliku: {source}")

    engine = get_engine(source)

    # np. summary_ENDGAME.ods -> folder summary_ENDGAME
    if output_dir is None:
        output_dir = source.parent / source.stem

    output_dir.mkdir(parents=True, exist_ok=True)

    used_names: set[str] = set()

    with pd.ExcelFile(source, engine=engine) as workbook:
        if verbose:
            print(f"\nPlik: {source.name}")
            print(f"Liczba arkuszy: {len(workbook.sheet_names)}")
            print(f"Folder wyjściowy: {output_dir}\n")

        for sheet_name in workbook.sheet_names:
            # header=None zachowuje cały arkusz, także pierwszy wiersz.
            df = pd.read_excel(
                workbook,
                sheet_name=sheet_name,
                header=None,
                dtype=object,
                na_filter=False,
            )

            file_stem = safe_filename(sheet_name)
            csv_name = f"{file_stem}.csv"

            # Zabezpieczenie, gdy dwa arkusze po oczyszczeniu nazw mają tę samą nazwę.
            counter = 2
            while csv_name.lower() in used_names:
                csv_name = f"{file_stem}_{counter}.csv"
                counter += 1

            used_names.add(csv_name.lower())

            output_path = output_dir / csv_name

            df.to_csv(
                output_path,
                index=False,
                header=False,
                encoding="utf-8-sig",
            )

            if verbose:
                print(f"OK: {sheet_name} -> {csv_name}")

    return output_dir


def main():
    parser = argparse.ArgumentParser(
        description="Eksportuje wszystkie arkusze z pliku ODS lub XLSX do osobnych CSV."
    )

    parser.add_argument(
        "input_file",
        help="Ścieżka do pliku .ods, .xlsx lub .xlsm",
    )

    parser.add_argument(
        "--output",
        help="Opcjonalna ścieżka do folderu wyjściowego",
        default=None,
    )

    args = parser.parse_args()

    try:
        source = Path(args.input_file)
        output_dir = Path(args.output) if args.output else None

        created_dir = export_workbook(source, output_dir)
        print(f"\nGotowe. Pliki CSV zapisano w:\n{created_dir}")

    except Exception as error:
        print(f"\nBŁĄD: {error}")
        sys.exit(1)


if __name__ == "__main__":
    main()
