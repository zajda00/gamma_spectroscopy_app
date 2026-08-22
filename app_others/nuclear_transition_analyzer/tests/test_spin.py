from nuclear_transition_analyzer.spin import parse_spin_parity


def labels(text):
    return [(c.label, c.certainty) for c in parse_spin_parity(text)]


def test_exact_spin():
    assert labels("2+") == [("2+", "exact")]


def test_parenthesized_spin_is_suggestion():
    assert labels("(8-)") == [("8-", "suggested")]


def test_multiple_suggestions():
    got = labels("(3,4+),(5-)")
    assert ("3+", "suggested") in got
    assert ("4+", "suggested") in got
    assert ("5-", "suggested") in got


def test_parity_only_parentheses_suggested():
    assert labels("0(+)") == [("0+", "suggested")]
