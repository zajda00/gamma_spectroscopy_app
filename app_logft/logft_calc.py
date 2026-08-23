from __future__ import annotations
import math
from dataclasses import dataclass
from typing import Optional, Tuple
from scipy.integrate import quad
from dataclasses import asdict
import re

try:
    # helper to parse Z from symbol when available
    from app_decay_scheme.periodic_table import z_from_symbol
except Exception:
    try:
        from .periodic_table import z_from_symbol
    except Exception:
        def z_from_symbol(sym: str) -> int:
            return 0

# Constants
ME_KEV = 510.99895  # electron mass energy in keV
ALPHA = 1.0 / 137.035999084  # fine-structure constant
PI = math.pi


@dataclass
class LogftResult:
    f_value: Optional[float]
    logft: Optional[float]


def _fermi_factor(Z: int, w: float) -> float:
    """Approximate Fermi function factor F(Z,w).

    Uses the simple Coulomb factor approximation
    F = 2*pi*eta / (1 - exp(-2*pi*eta)) where
    eta = alpha * Z * w / p, p = sqrt(w^2 - 1).

    This is a pragmatic improvement over F=1 and works well
    for moderate Z and endpoint energies.
    """
    p2 = max(w * w - 1.0, 0.0)
    if p2 <= 0.0:
        return 1.0
    p = math.sqrt(p2)
    # avoid division by zero
    if p == 0.0:
        return 1.0
    eta = ALPHA * Z * w / p
    # protect against extreme eta
    x = 2.0 * PI * eta
    if x == 0.0:
        return 1.0
    # numerical safety for large negative x
    try:
        denom = 1.0 - math.exp(-x)
        if denom == 0.0:
            return 1.0
        return x / denom
    except OverflowError:
        return 1.0


def fermi_integral_allowed(endpoint_keV: float, Z: int) -> Optional[float]:
    """Compute phase-space integral f for allowed beta decay.

    endpoint_keV: maximum kinetic energy of beta particle (keV)
    Z: atomic number of daughter nucleus (use positive for beta-)

    Returns dimensionless f or None if endpoint not physical.
    """
    if endpoint_keV <= 0.0:
        return None
    w0 = 1.0 + endpoint_keV / ME_KEV

    def integrand(w: float) -> float:
        p2 = max(w * w - 1.0, 0.0)
        p = math.sqrt(p2)
        # basic allowed shape: p * w * (w0 - w)^2
        shape = p * w * (w0 - w) ** 2
        F = _fermi_factor(Z, w)
        return shape * F

    val, err = quad(integrand, 1.0, w0, limit=200)
    return float(val)


def compute_logft_from_values(endpoint_keV: float, half_life_s: float, branch_fraction: float, Z: int) -> LogftResult:
    """Compute `f` and `logft` for a single beta branch.

    - `endpoint_keV`: beta endpoint kinetic energy in keV
    - `half_life_s`: parent half-life in seconds
    - `branch_fraction`: branching fraction (0..1)
    - `Z`: atomic number of daughter nucleus (positive for beta-)

    Returns LogftResult with `f_value` and `logft` (base-10) or None if not computable.
    """
    if branch_fraction <= 0.0 or half_life_s <= 0.0:
        return LogftResult(None, None)
    f = fermi_integral_allowed(endpoint_keV, Z)
    if f is None or f <= 0.0:
        return LogftResult(f, None)
    # partial half-life t_p = T1/2 / branch_fraction
    t_partial = half_life_s / branch_fraction
    ft = f * t_partial
    try:
        logft = math.log10(ft)
    except (ValueError, OverflowError):
        logft = None
    return LogftResult(f_value=f, logft=logft)


def compute_logft_for_parent_level(qbeta_keV: float, parent_exc_keV: float, level_e_keV: float, half_life_ms: float, branch_percent: float, daughter_Z: int) -> LogftResult:
    """Convenience wrapper matching typical inputs used in the app.

    - `qbeta_keV`: Q_beta for parent nucleus (keV)
    - `parent_exc_keV`: excitation energy of parent state (keV)
    - `level_e_keV`: excitation energy of daughter level (keV)
    - `half_life_ms`: half-life of parent state in milliseconds
    - `branch_percent`: branch intensity in percent (0..100)
    - `daughter_Z`: atomic number of daughter nucleus

    Returns LogftResult.
    """
    endpoint = qbeta_keV + parent_exc_keV - level_e_keV
    half_life_s = half_life_ms / 1000.0
    branch_fraction = branch_percent / 100.0
    return compute_logft_from_values(endpoint, half_life_s, branch_fraction, daughter_Z)


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
    logft: float | None


def compute_logft(levels, abf_rows, beta_inputs) -> list[CompatLogftResult]:
    """Compatibility wrapper matching previous app_decay_scheme.logft.compute_logft signature.

    Uses `compute_logft_for_parent_level` internally. Attempts to infer daughter Z from
    `beta_inputs.daughter_z` or `beta_inputs.daughter_nucleus`.
    """
    abf_by_level = {r.level_id: getattr(r, 'abf_clipped', 0.0) for r in abf_rows}
    out: list[CompatLogftResult] = []

    # infer daughter Z
    daughter_z = 0
    if hasattr(beta_inputs, 'daughter_z') and beta_inputs.daughter_z:
        daughter_z = beta_inputs.daughter_z
    else:
        try:
            # try parse symbol like '122Ag'
            if hasattr(beta_inputs, 'daughter_nucleus'):
                txt = beta_inputs.daughter_nucleus
                # extract symbol
                import re
                m = re.match(r'^\d+([A-Za-z]+)$', txt.strip())
                if m:
                    sym = m.group(1)
                    daughter_z = z_from_symbol(sym) or 0
        except Exception:
            daughter_z = 0

    parent_states = _coerce_parent_states(beta_inputs)

    # import allowed_parent_states from app_decay_scheme.spin_rules
    try:
        from app_decay_scheme.spin_rules import allowed_parent_states
    except Exception:
        from . import app_decay_scheme  # fallback; will likely fail

    for lv in levels:
        branch = abf_by_level.get(lv.level_id, 0.0)
        if branch <= 0:
            continue
        parents = allowed_parent_states(parent_states, lv.jpi)
        if not parents:
            parents = [(s, 'no_simple_match') for s in parent_states if getattr(s, 'include_in_analysis', True)]
        for parent, cls in parents:
            endpoint = beta_inputs.qbeta_keV + parent.excitation_energy_keV - lv.e_level_keV
            res = compute_logft_for_parent_level(beta_inputs.qbeta_keV, parent.excitation_energy_keV, lv.e_level_keV, parent.half_life_ms, branch, daughter_z)
            out.append(CompatLogftResult(
                level_id=lv.level_id,
                e_level_keV=lv.e_level_keV,
                level_jpi=lv.jpi,
                parent_state_id=parent.state_id,
                parent_jpi=parent.jpi,
                endpoint_keV=endpoint,
                branch_percent=branch,
                classification=cls,
                logft=res.logft
            ))
    return out


def _parse_half_life_to_seconds(raw_value: object) -> float:
    """Parse half-life strings like '0.72 s', '5 ms', '1.2(3) s' into seconds."""
    if raw_value is None:
        return 0.0
    text = str(raw_value).strip()
    if not text:
        return 0.0
    text = text.replace('−', '-')
    match = re.search(r'(?P<value>\d+(?:\.\d+)?)\s*(?P<unit>min|ms|µs|us|ns|ps|yr|y|h|d|m|s)?', text, flags=re.I)
    if not match:
        return 0.0
    value = float(match.group('value'))
    unit = (match.group('unit') or 's').lower()
    unit_map = {'ps': 1e-12, 'ns': 1e-9, 'us': 1e-6, 'µs': 1e-6, 'ms': 1e-3, 's': 1.0, 'min': 60.0, 'm': 60.0, 'h': 3600.0, 'd': 86400.0, 'y': 31557600.0, 'yr': 31557600.0}
    return value * unit_map.get(unit, 1.0)


def _coerce_parent_states(beta_inputs) -> list:
    """Return parent states usable by logft calculation from either loaded project data or settings UI."""
    if getattr(beta_inputs, 'parent_states', None):
        return list(beta_inputs.parent_states)

    states = []
    mother_states = getattr(beta_inputs, 'mother_states', None) or []
    for idx, state in enumerate(mother_states, start=1):
        if not isinstance(state, dict):
            continue
        jpi = str(state.get('jpi') or state.get('spin_parity') or state.get('Jpi') or 'unknown').strip()
        if not jpi and isinstance(state.get('spin_parity'), str):
            jpi = str(state['spin_parity']).strip()
        exc = state.get('e_keV') or state.get('energy_keV') or state.get('energy') or state.get('e') or 0.0
        t12 = state.get('t12') or state.get('half_life') or state.get('half_life_display') or ''
        try:
            exc_keV = float(exc)
        except (TypeError, ValueError):
            exc_keV = 0.0
        half_life_ms = _parse_half_life_to_seconds(t12) * 1000.0
        states.append(type('ParentStateLike', (), {
            'state_id': f'parent_state_{idx}',
            'jpi': jpi or 'unknown',
            'excitation_energy_keV': exc_keV,
            'half_life_ms': half_life_ms,
            'include_in_analysis': True,
        })())

    if states:
        return states

    # Final fallback: synthesize a single generic parent state when only basic settings are available.
    return [type('ParentStateLike', (), {
        'state_id': 'parent_state_1',
        'jpi': str(getattr(beta_inputs, 'mother_spin_display', '') or getattr(beta_inputs, 'mother_spinpar', '') or 'unknown').strip() or 'unknown',
        'excitation_energy_keV': 0.0,
        'half_life_ms': 0.0,
        'include_in_analysis': True,
    })()]
