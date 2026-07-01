from __future__ import annotations
import re
from dataclasses import dataclass

SPIN_RE = re.compile(r'(\d+)(?:/(\d+))?\s*([+-])')

@dataclass
class SpinCandidate:
    j: float
    parity: str
    label: str


def parse_spin_candidates(text: str) -> list[SpinCandidate]:
    candidates = []
    for m in SPIN_RE.finditer(text or ''):
        num = float(m.group(1))
        den = float(m.group(2)) if m.group(2) else 1.0
        j = num / den
        parity = m.group(3)
        candidates.append(SpinCandidate(j=j, parity=parity, label=m.group(0)))
    return candidates


def classify_forbiddenness(parent_jpi: str, daughter_jpi: str) -> tuple[bool, str]:
    p = parse_spin_candidates(parent_jpi)
    d = parse_spin_candidates(daughter_jpi)
    if not p or not d:
        return True, 'unknown_spin'
    best = None
    for pc in p:
        for dc in d:
            dj = abs(pc.j - dc.j)
            parity_change = pc.parity != dc.parity
            if not parity_change and dj in (0, 1):
                label = 'allowed'
                score = 0
            elif parity_change and dj <= 2:
                label = 'first_forbidden_or_first_forbidden_unique'
                score = 1
            elif (not parity_change and dj == 2) or (parity_change and dj == 3):
                label = 'second_forbidden_like'
                score = 2
            else:
                label = 'higher_forbidden_or_unlikely'
                score = 3
            if best is None or score < best[0]:
                best = (score, label)
    return best[0] <= 2, best[1]


def allowed_parent_states(parent_states, level_jpi: str):
    if not (level_jpi or '').strip():
        return [(s, 'level_spin_unknown') for s in parent_states if s.include_in_analysis]
    out = []
    for s in parent_states:
        if not s.include_in_analysis:
            continue
        ok, label = classify_forbiddenness(s.jpi, level_jpi)
        if ok:
            out.append((s, label))
    return out
