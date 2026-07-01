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
    payload = yaml.safe_load(_strip_markdown_fences(text))
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
    ns = payload.get('sNucl')
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
        show_neutron_separation=bool(payload.get('sNuclShow', False)),
        ground_state_strategy=(payload.get('ground_state_feeding', {}) or {}).get('estimation_method', 'closure_to_100'),
        normalization_reference_keV=float((payload.get('normalization', {}) or {}).get('reference_transition_keV', 0.0) or 0.0),
        # New fields with defaults
        mother_a=0,
        mother_z=0,
        mother_n=0,
        daughter_a=0,
        daughter_z=0,
        daughter_n=0,
        mother_t12='',
        mother_spinpar='',
        mother_q='',
        mother_sn='',
        mother_pn='',
        decay_channel=2,
        separation_energy_type='n',
    )


def dump_beta_inputs_to_text(beta: BetaInputs) -> str:
    payload = dict(beta.raw)
    payload['parent_nucleus'] = beta.parent_nucleus
    payload['daughter_nucleus'] = beta.daughter_nucleus
    payload['qbeta_keV'] = beta.qbeta_keV
    payload['dqbeta_keV'] = beta.dqbeta_keV
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
    return yaml.safe_dump(payload, sort_keys=False, allow_unicode=True)
