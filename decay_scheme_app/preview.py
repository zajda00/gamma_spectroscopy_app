from __future__ import annotations
import shutil
import subprocess
from pathlib import Path


def find_ghostscript_executable() -> str | None:
    for name in ('gs', 'gswin64c', 'gswin32c'):
        p = shutil.which(name)
        if p:
            return p
    return None


def ghostscript_available() -> bool:
    return find_ghostscript_executable() is not None


def render_eps_to_png(eps_path: Path, png_path: Path) -> tuple[bool, str]:
    exe = find_ghostscript_executable()
    if not exe:
        return False, 'Ghostscript executable not found in PATH.'

    cmd = [
        exe,
        '-dSAFER',
        '-dBATCH',
        '-dNOPAUSE',
        '-dEPSCrop',
        '-r150',
        '-sDEVICE=pngalpha',
        f'-sOutputFile={png_path}',
        str(eps_path),
    ]

    try:
        proc = subprocess.run(
            cmd,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )

        if proc.returncode == 0 and png_path.exists():
            return True, f'Ghostscript OK: {exe}'

        cmd_str = ' '.join(cmd)
        message = (
            f'Ghostscript failed with code {proc.returncode}\n'
            f'CMD: {cmd_str}\n'
            f'STDOUT:\n{proc.stdout}\n'
            f'STDERR:\n{proc.stderr}'
        )
        return False, message

    except Exception as exc:
        return False, f'Ghostscript invocation error: {exc}'

