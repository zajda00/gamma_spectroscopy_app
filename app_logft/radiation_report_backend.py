from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)


# Support both the updated module name and the older project name.
try:
    from .logft_calc_updated import LogftResult
except ImportError:
    try:
        from .logft_calc import LogftResult
    except ImportError:
        try:
            from logft_calc_updated import LogftResult
        except ImportError:
            try:
                from logft_calc import LogftResult
            except ImportError:

                @dataclass
                class LogftResult:
                    f_value: Optional[float]
                    logft: Optional[float]
                    backend: str = "radiation_report"


JAR_NAME = "RadiationReport_09December2025.jar"
BRIDGE_CLASS = "RadiationReportBridge"
BRIDGE_SOURCE_NAME = f"{BRIDGE_CLASS}.java"


def _parse_numeric(value: Any) -> Optional[float]:
    """Convert a scalar value to float, returning None for invalid values."""
    if value is None or value == "":
        return None

    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _format_optional(value: Optional[float]) -> str:
    """Format an optional numeric command-line value."""
    return "" if value is None else str(value)


def _candidate_bridge_dirs() -> list[Path]:
    """Return likely RadiationReport directories for the project layout.

    The application is commonly started from app_decay_scheme, while the JAR
    and Java bridge are stored in the sibling directory app_logft/radiationreport.
    The first candidate also supports keeping everything inside one package.
    """
    module_dir = Path(__file__).resolve().parent
    candidates = [
        module_dir / "radiationreport",
        module_dir.parent / "app_logft" / "radiationreport",
        module_dir.parent / "radiationreport",
        module_dir.parent.parent / "app_logft" / "radiationreport",
    ]

    unique: list[Path] = []
    for candidate in candidates:
        candidate = candidate.resolve()
        if candidate not in unique:
            unique.append(candidate)
    return unique


def _resolve_radiation_report_paths() -> tuple[Optional[Path], Optional[Path], str]:
    """Find the JAR and bridge directory without depending on cwd."""
    for bridge_dir in _candidate_bridge_dirs():
        jar_path = bridge_dir / JAR_NAME
        bridge_class_file = bridge_dir / f"{BRIDGE_CLASS}.class"
        bridge_source_file = bridge_dir / BRIDGE_SOURCE_NAME

        if jar_path.is_file() and (
            bridge_class_file.is_file() or bridge_source_file.is_file()
        ):
            return jar_path, bridge_dir, BRIDGE_CLASS

    return None, None, BRIDGE_CLASS


def _ensure_bridge_class(
    java_cmd: str,
    jar_path: Path,
    bridge_dir: Path,
) -> bool:
    """Compile the bridge source when a class file is not present."""
    bridge_class_file = bridge_dir / f"{BRIDGE_CLASS}.class"
    if bridge_class_file.is_file():
        return True

    bridge_source_file = bridge_dir / BRIDGE_SOURCE_NAME
    javac_cmd = shutil.which("javac")
    if not javac_cmd or not bridge_source_file.is_file():
        logger.debug(
            "RadiationReport bridge is not compiled and no usable Java source/compiler was found"
        )
        return False

    try:
        completed = subprocess.run(
            [
                javac_cmd,
                "-cp",
                str(jar_path),
                "-d",
                str(bridge_dir),
                str(bridge_source_file),
            ],
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
    except (FileNotFoundError, subprocess.SubprocessError, OSError) as exc:
        logger.debug("Could not compile RadiationReport bridge: %s", exc)
        return False

    if completed.returncode != 0:
        logger.debug(
            "RadiationReport bridge compilation failed: %s",
            completed.stderr.strip(),
        )
        return False

    return bridge_class_file.is_file()


def maybe_compute_via_radiation_report(
    endpoint_keV: float,
    half_life_s: float,
    branch_fraction: float,
    daughter_Z: int,
    *,
    parent_nucleus: Optional[str] = None,
    daughter_nucleus: Optional[str] = None,
    qbeta_keV: Optional[float] = None,
    parent_exc_keV: float = 0.0,
    level_e_keV: float = 0.0,
    parent_jpi: Optional[str] = None,
    daughter_jpi: Optional[str] = None,
    dqbeta_keV: Optional[float] = None,
    dhalf_life_s: Optional[float] = None,
    dparent_exc_keV: Optional[float] = None,
    dlevel_e_keV: Optional[float] = None,
    dbranch_percent: Optional[float] = None,
) -> Optional[LogftResult]:
    """Attempt to calculate log ft using the local RadiationReport bridge.

    Returns None when the reference backend cannot be used, allowing the
    caller to fall back to the local analytical implementation.
    """
    java_cmd = shutil.which("java")
    if not java_cmd:
        logger.debug("Java executable was not found")
        return None

    jar_path, bridge_dir, bridge_class = _resolve_radiation_report_paths()
    if jar_path is None or bridge_dir is None:
        logger.debug(
            "RadiationReport files were not found. Checked: %s",
            ", ".join(str(path) for path in _candidate_bridge_dirs()),
        )
        return None

    if not parent_nucleus or not daughter_nucleus:
        logger.debug("Parent and daughter nuclide names are required")
        return None

    if not _ensure_bridge_class(java_cmd, jar_path, bridge_dir):
        return None

    q_value = (
        qbeta_keV
        if qbeta_keV is not None
        else endpoint_keV - parent_exc_keV + level_e_keV
    )

    if q_value <= 0.0:
        logger.debug("Non-positive Q_beta value: %s", q_value)
        return None

    # os.pathsep gives ':' on Linux/macOS and ';' on Windows.
    classpath = os.pathsep.join(
        [
            str(jar_path),
            str(bridge_dir),
        ]
    )

    command = [
        java_cmd,
        "-cp",
        classpath,
        bridge_class,
        f"parent={parent_nucleus}",
        f"daughter={daughter_nucleus}",
        f"q={q_value}",
        f"dq={_format_optional(dqbeta_keV)}",
        f"parent_energy={parent_exc_keV}",
        f"dparent_energy={_format_optional(dparent_exc_keV)}",
        f"half_life={half_life_s}",
        f"dhalf_life={_format_optional(dhalf_life_s)}",
        "half_life_unit=S",
        f"level_energy={level_e_keV}",
        f"dlevel_energy={_format_optional(dlevel_e_keV)}",
        f"beta_intensity={branch_fraction * 100.0}",
        f"dbeta_intensity={_format_optional(dbranch_percent)}",
        f"parent_jpi={parent_jpi or ''}",
        f"daughter_jpi={daughter_jpi or ''}",
        "uniqueness=NO",
        "decay_mode=B-",
    ]

    try:
        completed = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except (FileNotFoundError, subprocess.SubprocessError, OSError) as exc:
        logger.debug("RadiationReport process failed: %s", exc)
        return None

    if completed.returncode != 0:
        logger.debug(
            "RadiationReport returned code %s: %s",
            completed.returncode,
            completed.stderr.strip(),
        )
        return None

    raw_output = completed.stdout.strip()
    if not raw_output:
        logger.debug("RadiationReport returned empty output")
        return None

    try:
        payload = json.loads(raw_output)
    except json.JSONDecodeError:
        logger.debug("RadiationReport returned invalid JSON: %r", raw_output[:500])
        return None

    if not isinstance(payload, dict) or not payload.get("ok"):
        logger.debug("RadiationReport response was not successful: %r", payload)
        return None

    logft_payload = payload.get("logft") or {}
    if not isinstance(logft_payload, dict):
        return None

    candidate = _parse_numeric(logft_payload.get("x"))
    if candidate is None:
        candidate = _parse_numeric(logft_payload.get("s"))
    if candidate is None:
        return None

    f_payload = payload.get("f_total") or payload.get("fTotal") or {}
    if not isinstance(f_payload, dict):
        return None

    f_value = _parse_numeric(f_payload.get("x"))
    if f_value is None:
        f_value = _parse_numeric(f_payload.get("s"))
    if f_value is None:
        return None

    return LogftResult(
        f_value=f_value,
        logft=candidate,
        backend="radiation_report",
    )


__all__ = ["maybe_compute_via_radiation_report"]
