from __future__ import annotations
import yaml
from .models import BetaInputs, ParentState


def _strip_markdown_fences(text: str) -> str:
    text = text.strip()
    if text.startswith('```'):
        lines = text.splitlines()
        if lines and lines[0].startswith('```'):
            lines = lines[1:]
        if lines and lines[-1].startswith('```'):
            lines = lines[:-1]
        text = '\n'.join(lines)
    return text


def load_beta_inputs_from_text(text: str) -> BetaInputs:
    payload = yaml.safe_load(_strip_markdown_fences(text)) or {}
    if not isinstance(payload, dict):
        payload = {}
    states = [ParentState(
        state_id=s.get('state_id', ''),
        jpi=s.get('Jpi', ''),
        excitation_energy_keV=float(s.get('excitation_energy_keV', 0.0) or 0.0),
        dexcitation_energy_keV=float(s.get('dexcitation_energy_keV', 0.0) or 0.0),
        half_life_ms=float(s.get('half_life_ms', 0.0) or 0.0),
        dhalf_life_ms=float(s.get('dhalf_life_ms', 0.0) or 0.0),
        include_in_analysis=bool(s.get('include_in_analysis', True)),
        notes=s.get('notes', ''),
    ) for s in payload.get('parent_states', [])]
    included = [s for s in states if s.include_in_analysis]
    mother_spin_display = ', '.join(s.jpi for s in included)
    mother_half_life_display = '; '.join(f"{s.jpi}: {s.half_life_ms:g} ms" for s in included)
    legacy_mother_states = payload.get('mother_states') or []
    if not legacy_mother_states and (payload.get('mother_spinpar') or payload.get('mother_t12')):
        legacy_mother_states = []
        spin_values = [v.strip() for v in str(payload.get('mother_spinpar', '')).split(';') if v.strip()]
        t12_values = [v.strip() for v in str(payload.get('mother_t12', '')).split(';') if v.strip()]
        for idx, spin in enumerate(spin_values):
            t12 = t12_values[idx] if idx < len(t12_values) else ''
            if spin or t12:
                legacy_mother_states.append({'jpi': spin, 't12': t12})
        if not legacy_mother_states and (payload.get('mother_spinpar') or payload.get('mother_t12')):
            legacy_mother_states = [{'jpi': str(payload.get('mother_spinpar', '')).strip(), 't12': str(payload.get('mother_t12', '')).strip()}]
    ns = payload.get('sNucl')
    if ns is None:
        ns = payload.get('neutron_separation_energy_keV')
    ground_state_cfg = payload.get('ground_state_feeding', {}) or {}
    gs_mode = str(ground_state_cfg.get('mode', 'closure_to_100') or 'closure_to_100')
    manual_percent = ground_state_cfg.get('manual_ground_state_feeding_percent')
    if manual_percent is None:
        manual_percent = ground_state_cfg.get('fitted_ground_state_feeding_percent')
    if manual_percent is None:
        manual_percent = ground_state_cfg.get('initial_ground_state_feeding_percent')
    mother_a = payload.get('mother_a', 0)
    mother_z = payload.get('mother_z', 0)
    mother_n = payload.get('mother_n', 0)
    daughter_a = payload.get('daughter_a', 0)
    daughter_z = payload.get('daughter_z', 0)
    daughter_n = payload.get('daughter_n', 0)
    return BetaInputs(
        raw=payload,
        parent_nucleus=payload.get('parent_nucleus', ''),
        daughter_nucleus=payload.get('daughter_nucleus', ''),
        qbeta_keV=float(payload.get('qbeta_keV', 0.0) or 0.0),
        dqbeta_keV=float(payload.get('dqbeta_keV', 0.0) or 0.0),
        parent_states=states,
        mother_spin_display=mother_spin_display,
        mother_half_life_display=mother_half_life_display,
        neutron_separation_energy_keV=float(ns) if ns not in (None, '') else None,
        neutron_separation_energy_uncertainty_keV=float(payload.get('neutron_separation_energy_uncertainty_keV', payload.get('dsn_keV', 0.0) or 0.0)) if payload.get('neutron_separation_energy_uncertainty_keV', payload.get('dsn_keV', 0.0)) not in (None, '') else None,
        show_neutron_separation=bool(payload.get('sNuclShow', payload.get('show_neutron_separation', False))),
        ground_state_strategy=(payload.get('ground_state_feeding', {}) or {}).get('estimation_method', 'closure_to_100'),
        ground_state_feeding_mode=gs_mode,
        manual_ground_state_feeding_percent=float(manual_percent) if manual_percent not in (None, '') else None,
        iterative_ground_state_feeding=bool(ground_state_cfg.get('iterative', False) or ground_state_cfg.get('iterative_ground_state_feeding', False)),
        iterative_tolerance=float(ground_state_cfg.get('tolerance', 0.01) or 0.01),
        iterative_max_iterations=int(ground_state_cfg.get('max_iterations', 20) or 20),
        normalization_reference_keV=float((payload.get('normalization', {}) or {}).get('reference_transition_keV', 0.0) or 0.0),
        mother_a=int(mother_a) if mother_a not in (None, '') else 0,
        mother_z=int(mother_z) if mother_z not in (None, '') else 0,
        mother_n=int(mother_n) if mother_n not in (None, '') else 0,
        daughter_a=int(daughter_a) if daughter_a not in (None, '') else 0,
        daughter_z=int(daughter_z) if daughter_z not in (None, '') else 0,
        daughter_n=int(daughter_n) if daughter_n not in (None, '') else 0,
        mother_t12=str(payload.get('mother_t12', '')),
        mother_spinpar=str(payload.get('mother_spinpar', '')),
        mother_q=str(payload.get('mother_q', '')),
        mother_sn=str(payload.get('mother_sn', '')),
        mother_pn=str(payload.get('mother_pn', '')),
        mother_states=legacy_mother_states,
        decay_channel=int(payload.get('decay_channel', 2) or 2),
        separation_energy_type=str(payload.get('separation_energy_type', 'n') or 'n'),
    )


def dump_beta_inputs_to_text(beta: BetaInputs) -> str:
    payload = dict(beta.raw)
    payload['parent_nucleus'] = beta.parent_nucleus
    payload['daughter_nucleus'] = beta.daughter_nucleus
    payload['qbeta_keV'] = beta.qbeta_keV
    payload['dqbeta_keV'] = beta.dqbeta_keV
    payload['decay_channel'] = beta.decay_channel
    payload['mother_a'] = beta.mother_a
    payload['mother_z'] = beta.mother_z
    payload['mother_n'] = beta.mother_n
    payload['daughter_a'] = beta.daughter_a
    payload['daughter_z'] = beta.daughter_z
    payload['daughter_n'] = beta.daughter_n
    payload['neutron_separation_energy_keV'] = beta.neutron_separation_energy_keV
    payload['neutron_separation_energy_uncertainty_keV'] = beta.neutron_separation_energy_uncertainty_keV
    payload['separation_energy_type'] = beta.separation_energy_type
    payload['sNuclShow'] = beta.show_neutron_separation
    payload['parent_states'] = [
        {
            'state_id': s.state_id,
            'Jpi': s.jpi,
            'excitation_energy_keV': s.excitation_energy_keV,
            'dexcitation_energy_keV': s.dexcitation_energy_keV,
            'half_life_ms': s.half_life_ms,
            'dhalf_life_ms': s.dhalf_life_ms,
            'include_in_analysis': s.include_in_analysis,
            'notes': s.notes,
        }
        for s in beta.parent_states
    ]
    payload['mother_states'] = beta.mother_states
    payload['mother_spinpar'] = beta.mother_spinpar
    payload['mother_t12'] = beta.mother_t12
    payload['mother_q'] = beta.mother_q
    payload['mother_sn'] = beta.mother_sn
    payload['mother_pn'] = beta.mother_pn
    return yaml.safe_dump(payload, sort_keys=False, allow_unicode=True)
