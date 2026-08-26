from __future__ import annotations

import logging
import math
import re
from dataclasses import dataclass
from typing import Any, Optional

from scipy.integrate import quad

logger = logging.getLogger(__name__)

try:
    from app_decay_scheme.periodic_table import z_from_symbol
except Exception:
    try:
        from .periodic_table import z_from_symbol
    except Exception:
        def z_from_symbol(symbol: str) -> int:
            return 0


ME_KEV = 510.99895
ALPHA = 1.0 / 137.035999084
PI = math.pi


@dataclass
class LogftResult:
    f_value: Optional[float]
    logft: Optional[float]
    backend: str = "local"


@dataclass
class CompatLogftResult:
    level_id: str
    e_level_keV: float
    level_jpi: str
    parent_state_id: str
    parent_jpi: str
    endpoint_keV: float
    branch_percent: float
    classification: str
    logft: Optional[float]
    backend: str = "local"


def _fermi_factor(Z: int, w: float) -> float:
    """Approximate Coulomb Fermi factor for beta-minus decay.

    This function is retained as a local fallback. Reference calculations
    should use the RadiationReport backend.
    """
    if Z <= 0 or w <= 1.0:
        return 1.0

    p2 = w * w - 1.0
    if p2 <= 0.0:
        return 1.0

    p = math.sqrt(p2)
    eta = ALPHA * Z * w / p
    x = 2.0 * PI * eta

    if abs(x) < 1e-14:
        return 1.0

    try:
        denominator = 1.0 - math.exp(-x)
        if denominator == 0.0:
            return 1.0
        return x / denominator
    except OverflowError:
        return 1.0


def fermi_integral_allowed(
    endpoint_keV: float,
    Z: int,
) -> Optional[float]:
    """Calculate the local approximate allowed beta-minus phase-space factor."""
    if endpoint_keV <= 0.0 or Z <= 0:
        return None

    w0 = 1.0 + endpoint_keV / ME_KEV

    def integrand(w: float) -> float:
        p2 = max(w * w - 1.0, 0.0)
        p = math.sqrt(p2)
        return p * w * (w0 - w) ** 2 * _fermi_factor(Z, w)

    value, _error = quad(
        integrand,
        1.0,
        w0,
        limit=200,
        epsabs=1e-10,
        epsrel=1e-8,
    )
    return float(value)


def _numeric(value: Any) -> Optional[float]:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _call_radiation_report(
    endpoint_keV: float,
    half_life_s: float,
    branch_fraction: float,
    daughter_Z: int,
    *,
    parent_nucleus: Optional[str],
    daughter_nucleus: Optional[str],
    qbeta_keV: Optional[float],
    parent_exc_keV: float,
    level_e_keV: float,
    parent_jpi: Optional[str],
    daughter_jpi: Optional[str],
) -> Optional[LogftResult]:
    """Call the optional local RadiationReport backend."""
    if not parent_nucleus or not daughter_nucleus:
        logger.debug("RadiationReport skipped: parent or daughter nucleus missing")
        return None

    try:
        from .radiation_report_backend import (
            maybe_compute_via_radiation_report,
        )
    except ImportError:
        try:
            from radiation_report_backend import (
                maybe_compute_via_radiation_report,
            )
        except ImportError:
            logger.debug("RadiationReport backend module is unavailable")
            return None

    try:
        result = maybe_compute_via_radiation_report(
            endpoint_keV=endpoint_keV,
            half_life_s=half_life_s,
            branch_fraction=branch_fraction,
            daughter_Z=daughter_Z,
            parent_nucleus=parent_nucleus,
            daughter_nucleus=daughter_nucleus,
            qbeta_keV=qbeta_keV,
            parent_exc_keV=parent_exc_keV,
            level_e_keV=level_e_keV,
            parent_jpi=parent_jpi,
            daughter_jpi=daughter_jpi,
        )
    except Exception:
        logger.exception("RadiationReport backend failed")
        return None

    if result is None:
        return None

    # The backend should already return LogftResult. This guard keeps the
    # integration safe if an older backend returns a compatible object.
    if isinstance(result, LogftResult):
        if not result.backend or result.backend == "local":
            result.backend = "radiation_report"
        return result

    candidate = _numeric(getattr(result, "logft", None))
    f_value = _numeric(getattr(result, "f_value", None))
    if candidate is None:
        return None

    return LogftResult(
        f_value=f_value,
        logft=candidate,
        backend="radiation_report",
    )


def compute_logft_from_values(
    endpoint_keV: float,
    half_life_s: float,
    branch_fraction: float,
    Z: int,
    *,
    backend: str = "local",
    parent_nucleus: Optional[str] = None,
    daughter_nucleus: Optional[str] = None,
    qbeta_keV: Optional[float] = None,
    parent_exc_keV: float = 0.0,
    level_e_keV: float = 0.0,
    parent_jpi: Optional[str] = None,
    daughter_jpi: Optional[str] = None,
) -> LogftResult:
    """Calculate logft using RadiationReport or the local fallback.

    The local implementation is only an approximate allowed beta-minus
    calculation. The reference backend is selected with backend="radiation_report".
    """
    if endpoint_keV <= 0.0:
        return LogftResult(None, None, backend="invalid_endpoint")

    if half_life_s <= 0.0:
        return LogftResult(None, None, backend="invalid_half_life")

    if not 0.0 < branch_fraction <= 1.0:
        return LogftResult(None, None, backend="invalid_branch_fraction")

    if not 5 <= Z <= 110:
        return LogftResult(None, None, backend="invalid_daughter_Z")

    if backend == "radiation_report":
        reference_result = _call_radiation_report(
            endpoint_keV=endpoint_keV,
            half_life_s=half_life_s,
            branch_fraction=branch_fraction,
            daughter_Z=Z,
            parent_nucleus=parent_nucleus,
            daughter_nucleus=daughter_nucleus,
            qbeta_keV=qbeta_keV,
            parent_exc_keV=parent_exc_keV,
            level_e_keV=level_e_keV,
            parent_jpi=parent_jpi,
            daughter_jpi=daughter_jpi,
        )
        if reference_result is not None:
            return reference_result

        logger.warning(
            "RadiationReport unavailable for this branch; using local approximation"
        )

    f_value = fermi_integral_allowed(endpoint_keV, Z)
    if f_value is None or f_value <= 0.0:
        return LogftResult(f_value, None, backend="local")

    partial_half_life = half_life_s / branch_fraction
    ft_value = f_value * partial_half_life

    try:
        logft = math.log10(ft_value)
    except (ValueError, OverflowError):
        logft = None

    return LogftResult(
        f_value=f_value,
        logft=logft,
        backend="local",
    )


def compute_logft_for_parent_level(
    qbeta_keV: float,
    parent_exc_keV: float,
    level_e_keV: float,
    half_life_ms: float,
    branch_percent: float,
    daughter_Z: int,
    *,
    backend: str = "local",
    parent_nucleus: Optional[str] = None,
    daughter_nucleus: Optional[str] = None,
    parent_jpi: Optional[str] = None,
    daughter_jpi: Optional[str] = None,
) -> LogftResult:
    """Calculate logft for one parent-to-daughter beta branch."""
    endpoint = (
        qbeta_keV
        + parent_exc_keV
        - level_e_keV
    )
    half_life_s = half_life_ms / 1000.0
    branch_fraction = branch_percent / 100.0

    return compute_logft_from_values(
        endpoint_keV=endpoint,
        half_life_s=half_life_s,
        branch_fraction=branch_fraction,
        Z=daughter_Z,
        backend=backend,
        parent_nucleus=parent_nucleus,
        daughter_nucleus=daughter_nucleus,
        qbeta_keV=qbeta_keV,
        parent_exc_keV=parent_exc_keV,
        level_e_keV=level_e_keV,
        parent_jpi=parent_jpi,
        daughter_jpi=daughter_jpi,
    )


def _get_value(obj: Any, *names: str, default: Any = None) -> Any:
    """Read a field from either a dictionary or an object."""
    for name in names:
        if isinstance(obj, dict):
            value = obj.get(name)
        else:
            value = getattr(obj, name, None)
        if value is not None:
            return value
    return default


def _coerce_parent_states(beta_inputs) -> list:
    """Return parent states usable by the logft calculation."""
    existing = getattr(beta_inputs, "parent_states", None)
    if existing:
        return list(existing)

    mother_states = getattr(beta_inputs, "mother_states", None) or []
    states = []

    for index, state in enumerate(mother_states, start=1):
        jpi = str(
            _get_value(
                state,
                "jpi_raw",
                "jpi",
                "spin_parity",
                "Jpi",
                default="unknown",
            )
            or "unknown"
        ).strip()

        excitation_raw = _get_value(
            state,
            "e_keV",
            "energy_keV",
            "energy",
            "e",
        )

        if excitation_raw is None or excitation_raw == "":
            excitation_keV = 0.0
        else:
            excitation_keV = _numeric(excitation_raw)
            if excitation_keV is None:
                logger.warning(
                    "Skipping parent state with non-numeric excitation energy: %r",
                    excitation_raw,
                )
                continue

        half_life_raw = _get_value(
            state,
            "t12",
            "half_life",
            "half_life_display",
            "half_life_seconds",
            default="",
        )

        if _numeric(half_life_raw) is not None and not isinstance(half_life_raw, str):
            half_life_s = float(half_life_raw)
        else:
            half_life_s = _parse_half_life_to_seconds(half_life_raw)

        state_id = str(
            _get_value(
                state,
                "state_id",
                "id",
                default=f"parent_state_{index}",
            )
        )

        states.append(
            type(
                "ParentStateLike",
                (),
                {
                    "state_id": state_id,
                    "jpi": jpi,
                    "excitation_energy_keV": excitation_keV,
                    "half_life_ms": half_life_s * 1000.0,
                    "include_in_analysis": bool(
                        _get_value(
                            state,
                            "include_in_analysis",
                            default=True,
                        )
                    ),
                },
            )()
        )

    if states:
        return states

    return [
        type(
            "ParentStateLike",
            (),
            {
                "state_id": "parent_state_1",
                "jpi": str(
                    getattr(beta_inputs, "mother_spin_display", "")
                    or getattr(beta_inputs, "mother_spinpar", "")
                    or "unknown"
                ).strip(),
                "excitation_energy_keV": 0.0,
                "half_life_ms": 0.0,
                "include_in_analysis": True,
            },
        )()
    ]


def _parse_half_life_to_seconds(raw_value: object) -> float:
    """Parse half-life strings into seconds.

    Supports units used by NuDat, including m for minutes and ms for
    milliseconds. Examples: 0.72 s, 5 ms, 37.230 m, 1.2 ps.
    """
    if raw_value is None:
        return 0.0

    text = str(raw_value).strip()
    if not text:
        return 0.0

    text = (
        text.replace("−", "-")
        .replace("µ", "u")
        .replace("μ", "u")
    )

    match = re.search(
        r"(?P<value>\d+(?:\.\d+)?)\s*"
        r"(?P<unit>min|ms|us|ns|ps|yr|y|h|d|m|s)?",
        text,
        flags=re.I,
    )

    if not match:
        return 0.0

    value = float(match.group("value"))
    unit = (match.group("unit") or "s").lower()

    unit_map = {
        "ps": 1e-12,
        "ns": 1e-9,
        "us": 1e-6,
        "ms": 1e-3,
        "s": 1.0,
        "m": 60.0,
        "min": 60.0,
        "h": 3600.0,
        "d": 86400.0,
        "y": 31557600.0,
        "yr": 31557600.0,
    }

    return value * unit_map.get(unit, 1.0)


def _infer_daughter_z(beta_inputs) -> int:
    explicit_z = getattr(beta_inputs, "daughter_z", None)
    if explicit_z:
        return int(explicit_z)

    nucleus = getattr(beta_inputs, "daughter_nucleus", None)
    if not nucleus:
        return 0

    match = re.match(r"^\d+([A-Za-z]+)$", str(nucleus).strip())
    if not match:
        return 0

    return int(z_from_symbol(match.group(1)) or 0)


def compute_logft(levels, abf_rows, beta_inputs) -> list[CompatLogftResult]:
    """Calculate logft for all beta-fed levels in the current scheme."""
    abf_by_level = {}
    for row in abf_rows:
        level_id = _get_value(row, "level_id")
        branch = _get_value(row, "abf_clipped", "abf", "branch_percent", default=0.0)
        branch_numeric = _numeric(branch)
        if level_id is not None and branch_numeric is not None:
            abf_by_level[level_id] = branch_numeric

    daughter_z = _infer_daughter_z(beta_inputs)
    parent_states = _coerce_parent_states(beta_inputs)

    try:
        from app_decay_scheme.spin_rules import allowed_parent_states
    except ImportError:
        try:
            from .spin_rules import allowed_parent_states
        except ImportError:
            allowed_parent_states = None
            logger.warning("spin_rules.allowed_parent_states is unavailable")

    parent_nucleus = (
        getattr(beta_inputs, "parent_nucleus", None)
        or getattr(beta_inputs, "mother_nucleus", None)
    )
    daughter_nucleus = getattr(beta_inputs, "daughter_nucleus", None)
    selected_backend = getattr(
        beta_inputs,
        "logft_backend",
        "radiation_report",
    )

    qbeta = _numeric(getattr(beta_inputs, "qbeta_keV", None))
    if qbeta is None:
        logger.error("Cannot calculate logft: qbeta_keV is missing")
        return []

    output: list[CompatLogftResult] = []

    for level in levels:
        level_id = getattr(level, "level_id", None)
        branch = abf_by_level.get(level_id, 0.0)
        level_energy = _numeric(getattr(level, "e_level_keV", None))

        if level_energy is None or branch <= 0.0:
            continue

        if allowed_parent_states is not None:
            parents = allowed_parent_states(parent_states, level.jpi)
        else:
            parents = []

        if not parents:
            parents = [
                (
                    state,
                    "no_simple_match",
                )
                for state in parent_states
                if getattr(state, "include_in_analysis", True)
            ]

        for parent, classification in parents:
            parent_energy = _numeric(
                getattr(parent, "excitation_energy_keV", None)
            )
            half_life_ms = _numeric(
                getattr(parent, "half_life_ms", None)
            )

            if parent_energy is None or half_life_ms is None:
                continue

            endpoint = qbeta + parent_energy - level_energy

            result = compute_logft_for_parent_level(
                qbeta_keV=qbeta,
                parent_exc_keV=parent_energy,
                level_e_keV=level_energy,
                half_life_ms=half_life_ms,
                branch_percent=branch,
                daughter_Z=daughter_z,
                backend=selected_backend,
                parent_nucleus=parent_nucleus,
                daughter_nucleus=daughter_nucleus,
                parent_jpi=getattr(parent, "jpi", None),
                daughter_jpi=getattr(level, "jpi", None),
            )

            output.append(
                CompatLogftResult(
                    level_id=level_id,
                    e_level_keV=level_energy,
                    level_jpi=getattr(level, "jpi", ""),
                    parent_state_id=getattr(parent, "state_id", ""),
                    parent_jpi=getattr(parent, "jpi", ""),
                    endpoint_keV=endpoint,
                    branch_percent=branch,
                    classification=classification,
                    logft=result.logft,
                    backend=result.backend,
                )
            )

    return output


__all__ = [
    "LogftResult",
    "CompatLogftResult",
    "fermi_integral_allowed",
    "compute_logft_from_values",
    "compute_logft_for_parent_level",
    "compute_logft",
]


if __name__ == "__main__":
    result = compute_logft_for_parent_level(
        qbeta_keV=9510.0,
        parent_exc_keV=0.0,
        level_e_keV=569.36,
        half_life_ms=529.0,
        branch_percent=5.0,
        daughter_Z=48,
        backend="local",
    )
    print(result)
