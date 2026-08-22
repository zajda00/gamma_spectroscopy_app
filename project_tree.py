from pathlib import Path
import sys

# Foldery będą widoczne w drzewie, ale ich zawartość nie zostanie rozwinięta.
SKIP_CONTENTS_FOR = {
    ".venv",
    ".vscode",
    ".git",
    "__pycache__",
    ".pytest_cache",
    ".mypy_cache",
    ".ruff_cache",
    "node_modules",
}

# Pliki techniczne, których nie chcemy pokazywać.
SKIP_FILES = {
    "project_tree.txt",
}

OUTPUT_FILE = "project_tree.txt"


def sorted_items(folder: Path):
    try:
        items = [item for item in folder.iterdir() if item.name not in SKIP_FILES]
        return sorted(items, key=lambda x: (not x.is_dir(), x.name.lower()))
    except PermissionError:
        return []


def build_tree(folder: Path, prefix: str = "") -> list[str]:
    lines = []
    items = sorted_items(folder)

    for index, item in enumerate(items):
        is_last = index == len(items) - 1
        connector = "└── " if is_last else "├── "
        next_prefix = prefix + ("    " if is_last else "│   ")

        if item.is_dir():
            if item.name in SKIP_CONTENTS_FOR:
                lines.append(f"{prefix}{connector}{item.name}/ [zawartość pominięta]")
            else:
                lines.append(f"{prefix}{connector}{item.name}/")
                lines.extend(build_tree(item, next_prefix))
        else:
            lines.append(f"{prefix}{connector}{item.name}")

    return lines


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    lines = [f"{root.name}/"]
    lines.extend(build_tree(root))

    output = "\n".join(lines)

    print(output)

    output_path = root / OUTPUT_FILE
    output_path.write_text(output + "\n", encoding="utf-8")

    print(f"\nZapisano również do: {output_path}")


if __name__ == "__main__":
    main()
