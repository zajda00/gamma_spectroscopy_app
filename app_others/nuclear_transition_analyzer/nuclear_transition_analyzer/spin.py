from __future__ import annotations

from dataclasses import dataclass
from fractions import Fraction
import math
import re
from typing import Iterable, Optional


@dataclass(frozen=True, order=True)
class SpinParity:
    """A spin-parity candidate from a data table.

    certainty is one of:
    - exact: no parentheses or brackets in the source candidate
    - suggested: candidate was enclosed in parentheses/brackets or had partial uncertainty
    - unknown: no usable spin/parity information
    """

    J: Fraction
    parity: Optional[str]
    certainty: str = "exact"
    raw: str = ""

    @property
    def label(self) -> str:
        if self.J.denominator == 1:
            j = str(self.J.numerator)
        else:
            j = f"{self.J.numerator}/{self.J.denominator}"
        return f"{j}{self.parity or '?'}"

    @property
    def is_exact(self) -> bool:
        return self.certainty == "exact" and self.parity in {"+", "-"}

    @property
    def is_suggested(self) -> bool:
        return self.certainty == "suggested"


_EMPTY = {"", "nan", "none", "unknown", "?", "()"}


def _clean(text: str) -> str:
    text = text.strip()
    text = text.replace("−", "-").replace("–", "-").replace("—", "-")
    text = text.replace("[", "(").replace("]", ")")
    text = text.replace(" ", "")
    return text


def _parse_j(token: str) -> Optional[Fraction]:
    m = re.search(r"(\d+)(?:/(\d+))?", token)
    if not m:
        return None
    if m.group(2):
        return Fraction(int(m.group(1)), int(m.group(2)))
    return Fraction(int(m.group(1)), 1)


def _candidate_certainty(text: str, start: int, end: int) -> str:
    """Mark candidate suggested if any parenthesis encloses it or touches its parity."""
    # Any parentheses anywhere in the raw candidate string are treated as uncertainty hints.
    # This is intentionally conservative: (4+), ((3+)), (2)+ and 0(+) are not hard constraints.
    if "(" in text or ")" in text:
        return "suggested"
    return "exact"


def parse_spin_parity(value) -> list[SpinParity]:
    if value is None:
        return []
    if isinstance(value, float) and math.isnan(value):
        return []
    raw = str(value).strip()
    if _clean(raw).lower() in _EMPTY:
        return []

    s = _clean(raw)
    # Split groups, but keep comma-separated J alternatives such as (3,4+).
    chunks = re.split(r";|/|\bor\b", s)
    results: list[SpinParity] = []

    for chunk in chunks:
        if not chunk:
            continue
        # Capture alternatives like 3,4+ or 5-.
        parts = [p for p in re.split(r",", chunk) if p]
        explicit_parities = [m.group(1) for p in parts if (m := re.search(r"([+-])", p))]
        shared_parity = explicit_parities[-1] if len(set(explicit_parities)) == 1 and explicit_parities else None
        for part in parts:
            j = _parse_j(part)
            if j is None:
                continue
            parity_match = re.search(r"([+-])", part)
            parity = parity_match.group(1) if parity_match else shared_parity
            cert = _candidate_certainty(s, 0, len(s))
            cand = SpinParity(J=j, parity=parity, certainty=cert, raw=raw)
            if cand not in results:
                results.append(cand)
    return results


def summarize_spin(value) -> str:
    cands = parse_spin_parity(value)
    if not cands:
        return "brak danych"
    exact = [c.label for c in cands if c.certainty == "exact"]
    suggested = [c.label for c in cands if c.certainty == "suggested"]
    pieces = []
    if exact:
        pieces.append("pewne: " + ", ".join(exact))
    if suggested:
        pieces.append("sugestie: " + ", ".join(suggested))
    return "; ".join(pieces)


def exact_candidates(value) -> list[SpinParity]:
    return [c for c in parse_spin_parity(value) if c.is_exact]


def suggested_candidates(value) -> list[SpinParity]:
    return [c for c in parse_spin_parity(value) if c.is_suggested]
