from __future__ import annotations
import math
from collections import defaultdict
from dataclasses import dataclass
from .models import Level, Transition

@dataclass
class AbfRow:
    level_id: str
    e_level_keV: float
    jpi: str
    incoming: float
    outgoing: float
    abf_raw: float
    abf_clipped: float
    mode: str
    warning: str = ''


def _transition_value(t: Transition, field: str) -> float:
    val = getattr(t, field, None)
    return float(val) if val is not None else 0.0


def _transition_sigma(t: Transition, field: str) -> float:
    """Estimate 1σ uncertainty of a transition intensity used in the ABF balance."""
    value = _transition_value(t, field)
    if value == 0.0:
        return 0.0

    if field == 'absolute_percent':
        err = getattr(t, 'absolute_err_percent', None)
    elif field == 'relative_percent':
        err = getattr(t, 'relative_err_percent', None)
    else:
        err = None

    if err is not None:
        try:
            return abs(float(err)) * abs(value) / 100.0
        except (TypeError, ValueError):
            pass

    integral_err = getattr(t, 'integral_err', None)
    if integral_err is not None:
        try:
            return abs(float(integral_err))
        except (TypeError, ValueError):
            pass

    return 0.0


def compute_abf(levels: list[Level], transitions: list[Transition], mode: str = 'no_ground_state', field: str = 'absolute_percent', ground_state_feeding_mode: str | None = None, manual_ground_state_feeding_percent: float | None = None, iterative_tolerance: float = 0.01, iterative_max_iterations: int = 20) -> list[AbfRow]:
    incoming = defaultdict(float)
    outgoing = defaultdict(float)
    incoming_sigma2 = defaultdict(float)
    outgoing_sigma2 = defaultdict(float)

    # Legacy compatibility: if caller passes a mode string like 'with_ground_state_closure',
    # that should still work as before. New project-level settings override only when given.
    effective_mode = ground_state_feeding_mode or mode

    for t in transitions:
        if not t.in_scheme:
            continue
        v = _transition_value(t, field)
        outgoing[t.level_initial_id] += v
        incoming[t.level_final_id] += v

        sig = _transition_sigma(t, field)
        outgoing_sigma2[t.level_initial_id] += sig * sig
        incoming_sigma2[t.level_final_id] += sig * sig

    rows = []
    for lv in sorted(levels, key=lambda x: x.e_level_keV):
        inc = incoming[lv.level_id]
        out = outgoing[lv.level_id]
        raw = out - inc
        sigma = math.sqrt(outgoing_sigma2[lv.level_id] + incoming_sigma2[lv.level_id])

        warning = ''
        clipped = raw
        if raw < 0.0:
            if sigma > 0 and abs(raw) <= 3.0 * sigma:
                clipped = 0.0
            else:
                warning = (
                    f"Negative apparent beta feeding {raw:.3f}% exceeds 3σ "
                    f"({(3.0 * sigma) if sigma > 0 else 0.0:.3f}%). Check scheme consistency."
                )
                if sigma <= 0:
                    warning = (
                        f"Negative apparent beta feeding {raw:.3f}% with no usable uncertainty estimate; "
                        "check scheme consistency."
                    )

        rows.append(AbfRow(lv.level_id, lv.e_level_keV, lv.jpi, inc, out, raw, clipped, mode, warning))

    if effective_mode in {'with_ground_state_closure', 'closure_to_100', 'manual', 'iterative', 'none'}:
        excited = [r for r in rows if r.e_level_keV > 0]
        gs_value = 0.0

        if effective_mode == 'manual' and manual_ground_state_feeding_percent is not None:
            gs_value = float(manual_ground_state_feeding_percent)
        elif effective_mode == 'none':
            gs_value = 0.0
        elif effective_mode == 'iterative':
            prev = 0.0
            for _ in range(max(1, iterative_max_iterations)):
                excited_sum = sum(max(r.abf_clipped, 0.0) for r in excited)
                gs_candidate = max(0.0, 100.0 - excited_sum)
                if abs(gs_candidate - prev) <= iterative_tolerance:
                    gs_value = gs_candidate
                    break
                prev = gs_candidate
                gs_value = gs_candidate
        else:
            gs_value = max(0.0, 100.0 - sum(max(r.abf_clipped, 0.0) for r in excited))

        for r in rows:
            if r.e_level_keV == 0.0:
                r.abf_raw = gs_value
                r.abf_clipped = gs_value
                if r.warning:
                    r.warning = ''

    return rows
