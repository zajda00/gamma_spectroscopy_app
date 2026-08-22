from __future__ import annotations

from dataclasses import dataclass
from fractions import Fraction
import math
from typing import Optional

from .spin import SpinParity

MULTIPOLES = [f"{kind}{L}" for L in range(1, 6) for kind in ("E", "M")]


def double_factorial(n: int) -> int:
    result = 1
    for k in range(n, 0, -2):
        result *= k
    return result


def multipole_kind(multipole: str) -> str:
    return multipole[0].upper()


def multipole_L(multipole: str) -> int:
    return int(multipole[1:])


def parity_allows(kind: str, L: int, pi_i: Optional[str], pi_f: Optional[str]) -> bool:
    if pi_i not in {"+", "-"} or pi_f not in {"+", "-"}:
        return True
    same = pi_i == pi_f
    if kind.upper() == "E":
        return same if L % 2 == 0 else not same
    return (not same) if L % 2 == 0 else same


def angular_momentum_allows(Ji: Fraction, Jf: Fraction, L: int) -> bool:
    if Ji == 0 and Jf == 0:
        return False
    return abs(Ji - Jf) <= L <= Ji + Jf


def transition_allows(initial: SpinParity, final: SpinParity, multipole: str) -> bool:
    L = multipole_L(multipole)
    return angular_momentum_allows(initial.J, final.J, L) and parity_allows(
        multipole_kind(multipole), L, initial.parity, final.parity
    )


def allowed_for_candidates(initial: list[SpinParity], final: list[SpinParity], multipole: str) -> bool:
    if not initial or not final:
        return True
    return any(transition_allows(i, f, multipole) for i in initial for f in final)


def weisskopf_B_reduced(multipole: str, A: float) -> float:
    """Single-particle Weisskopf reduced transition estimate.

    The absolute values are approximate and intended for ratios and order-of-magnitude
    comparisons, not for final B(XL) extraction.
    """
    kind = multipole_kind(multipole)
    L = multipole_L(multipole)
    if kind == "E":
        return (1.2 ** (2 * L) / (4 * math.pi)) * ((3 / (L + 3)) ** 2) * (A ** (2 * L / 3))
    return (10 / math.pi) * (1.2 ** (2 * L - 2)) * ((3 / (L + 3)) ** 2) * (A ** ((2 * L - 2) / 3))


def weisskopf_rate(multipole: str, energy_keV: float, A: float = 122) -> float:
    """Approximate gamma transition rate in Weisskopf units.

    Energies are in keV. Use ratios between branches, not the absolute value, as the
    robust observable in this application.
    """
    E_MeV = float(energy_keV) / 1000.0
    if E_MeV <= 0:
        return 0.0
    L = multipole_L(multipole)
    pref = 5.50e22 if multipole_kind(multipole) == "E" else 6.08e20
    geom = (L + 1) / (L * double_factorial(2 * L + 1) ** 2)
    energy_factor = (E_MeV / 197.3) ** (2 * L + 1)
    return pref * geom * energy_factor * weisskopf_B_reduced(multipole, A)


def order_of_magnitude_distance(observed_ratio: float, predicted_ratio: float) -> float:
    if observed_ratio <= 0 or predicted_ratio <= 0:
        return math.inf
    return abs(math.log10(predicted_ratio / observed_ratio))


def ratio_verdict(log10_distance: float) -> str:
    if math.isinf(log10_distance):
        return "brak oceny"
    if log10_distance <= 0.5:
        return "zgodne rzędem wielkości"
    if log10_distance <= 1.0:
        return "możliwe, różnica ok. rzędu wielkości"
    if log10_distance <= 2.0:
        return "słabe dopasowanie"
    return "raczej niezgodne"


def lower_multipole_penalty(multipole: str) -> float:
    L = multipole_L(multipole)
    kind = multipole_kind(multipole)
    return 0.08 * max(0, L - 1) + (0.02 if kind == "M" else 0.0)
