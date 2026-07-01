from __future__ import annotations
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


def _transition_value(t: Transition, field: str) -> float:
    val = getattr(t, field, None)
    return float(val) if val is not None else 0.0


def compute_abf(levels: list[Level], transitions: list[Transition], mode: str = 'no_ground_state', field: str = 'absolute_percent') -> list[AbfRow]:
    incoming = defaultdict(float)
    outgoing = defaultdict(float)
    for t in transitions:
        if not t.in_scheme:
            continue
        v = _transition_value(t, field)
        outgoing[t.level_initial_id] += v
        incoming[t.level_final_id] += v
    rows = []
    for lv in sorted(levels, key=lambda x: x.e_level_keV):
        inc = incoming[lv.level_id]
        out = outgoing[lv.level_id]
        raw = out - inc
        rows.append(AbfRow(lv.level_id, lv.e_level_keV, lv.jpi, inc, out, raw, max(raw, 0.0), mode))
    if mode == 'with_ground_state_closure':
        excited = [r for r in rows if r.e_level_keV > 0]
        closure = max(0.0, 100.0 - sum(r.abf_clipped for r in excited))
        for r in rows:
            if r.e_level_keV == 0.0:
                r.abf_raw = closure
                r.abf_clipped = closure
    return rows
