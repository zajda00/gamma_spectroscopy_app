from app_logft import logft_calc


def test_logft_basic():
    # Basic smoke test: endpoint 1000 keV, half-life 1 s, 100% branch, Z=47 (Ag)
    res = logft_calc.compute_logft_from_values(1000.0, 1.0, 1.0, 47)
    assert res.f_value is not None
    assert res.f_value > 0
    assert res.logft is not None


def test_logft_backend_falls_back_cleanly():
    res = logft_calc.compute_logft_from_values(1000.0, 1.0, 1.0, 47, backend='radiation_report')
    assert res.f_value is not None
    assert res.f_value > 0
    assert res.logft is not None
