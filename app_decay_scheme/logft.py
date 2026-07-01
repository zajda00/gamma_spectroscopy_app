from __future__ import annotations
import math
from dataclasses import dataclass
from scipy.integrate import quad
from .models import Level, ParentState
from .spin_rules import allowed_parent_states

ME_KEV = 510.99895

@dataclass
class LogftResult:
    level_id: str
    e_level_keV: float
    level_jpi: str
    parent_state_id: str
    parent_jpi: str
    endpoint_keV: float
    branch_percent: float
    classification: str
    logft: float | None


def _fermi_integral_allowed(q_keV: float) -> float | None:
    if q_keV <= 1.0:
        return None
    w0 = 1.0 + q_keV / ME_KEV
    def integrand(w: float) -> float:
        p2 = max(w*w - 1.0, 0.0)
        p = math.sqrt(p2)
        return p * w * (w0 - w) ** 2
    val, _ = quad(integrand, 1.0, w0, limit=200)
    return val


def compute_logft(levels, abf_rows, beta_inputs) -> list[LogftResult]:
    abf_by_level = {r.level_id: r.abf_clipped for r in abf_rows}
    out = []
    for lv in levels:
        branch = abf_by_level.get(lv.level_id, 0.0)
        if branch <= 0:
            continue
        parents = allowed_parent_states(beta_inputs.parent_states, lv.jpi)
        if not parents:
            parents = [(s, 'no_simple_match') for s in beta_inputs.parent_states if s.include_in_analysis]
        for parent, cls in parents:
            endpoint = beta_inputs.qbeta_keV + parent.excitation_energy_keV - lv.e_level_keV
            f = _fermi_integral_allowed(endpoint)
            if f is None or parent.half_life_ms <= 0:
                value = None
            else:
                branch_fraction = branch / 100.0
                t = parent.half_life_ms / 1000.0
                value = math.log10(f * t / branch_fraction) if branch_fraction > 0 else None
            out.append(LogftResult(
                level_id=lv.level_id,
                e_level_keV=lv.e_level_keV,
                level_jpi=lv.jpi,
                parent_state_id=parent.state_id,
                parent_jpi=parent.jpi,
                endpoint_keV=endpoint,
                branch_percent=branch,
                classification=cls,
                logft=value,
            ))
    return out
