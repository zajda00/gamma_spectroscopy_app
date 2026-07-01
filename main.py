from __future__ import annotations
import argparse
import sys
from pathlib import Path
from decay_scheme_app.loaders import ProjectDataLoader


def run_validate(folder: str) -> int:
    loader = ProjectDataLoader()
    project = loader.load_project(Path(folder))
    print(f"Loaded project from: {folder}")
    print(f"levels: {len(project.levels)}")
    print(f"transitions: {len(project.transitions)}")
    print(f"parent states: {len(project.beta_inputs.parent_states)}")
    print(f"uncertain peaks: {len(project.uncertain_rows)}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--validate', type=str, default=None, help='Load a project folder and print a quick validation summary.')
    args = parser.parse_args()
    if args.validate:
        return run_validate(args.validate)

    from PySide6.QtWidgets import QApplication
    from decay_scheme_app.main_window import MainWindow
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == '__main__':
    raise SystemExit(main())
