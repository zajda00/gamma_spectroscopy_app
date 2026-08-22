from fractions import Fraction

from nuclear_transition_analyzer.physics import transition_allows, weisskopf_rate
from nuclear_transition_analyzer.spin import SpinParity


def test_zero_to_zero_gamma_forbidden():
    assert not transition_allows(SpinParity(Fraction(0), "+"), SpinParity(Fraction(0), "+"), "E1")


def test_e2_2plus_to_0plus_allowed():
    assert transition_allows(SpinParity(Fraction(2), "+"), SpinParity(Fraction(0), "+"), "E2")


def test_e1_changes_parity():
    assert transition_allows(SpinParity(Fraction(1), "-"), SpinParity(Fraction(0), "+"), "E1")


def test_weisskopf_positive():
    assert weisskopf_rate("E2", 569.36, 122) > 0
