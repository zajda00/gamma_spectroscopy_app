from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

@dataclass
class ParentState:
    state_id: str
    jpi: str
    excitation_energy_keV: float = 0.0
    dexcitation_energy_keV: float = 0.0
    half_life_ms: float = 0.0
    dhalf_life_ms: float = 0.0
    include_in_analysis: bool = True
    notes: str = ''

@dataclass
class BetaInputs:
    raw: dict[str, Any]
    parent_nucleus: str
    daughter_nucleus: str
    qbeta_keV: float
    dqbeta_keV: float
    parent_states: list[ParentState] = field(default_factory=list)
    mother_spin_display: str = ''
    mother_half_life_display: str = ''
    neutron_separation_energy_keV: float | None = None
    show_neutron_separation: bool = False
    ground_state_strategy: str = 'closure_to_100'
    normalization_reference_keV: float | None = None

@dataclass
class Level:
    level_id: str
    nucleus: str
    e_level_keV: float
    de_level_keV: float | None = None
    jpi: str = ''
    jpi_origin_year: str = ''
    t12_s: float | None = None
    dt12_s: float | None = None
    comments: str = ''
    in_scheme: bool = True

@dataclass
class Transition:
    transition_id: str
    peak_id: str
    level_initial_id: str
    level_final_id: str
    decay_parent: str
    decay_daughter: str
    e_gamma_keV: float
    de_gamma_keV: float | None = None
    e_level_initial_keV: float | None = None
    e_level_final_keV: float | None = None
    origin: str = ''
    quality_flag: str = 'normal'
    comment: str = ''
    integral_counts: float | None = None
    integral_err: float | None = None
    relative_percent: float | None = None
    relative_err_percent: float | None = None
    absolute_percent: float | None = None
    absolute_err_percent: float | None = None
    emitted_plus_ic: float | None = None
    emitted_plus_ic_err: float | None = None
    alpha_tot: float | None = None
    multipolarity: str = ''
    in_scheme: bool = True

@dataclass
class ProjectData:
    root: Path
    beta_inputs: BetaInputs
    levels: list[Level]
    transitions: list[Transition]
    uncertain_rows: list[dict[str, Any]] = field(default_factory=list)
    literature_rows: list[dict[str, Any]] = field(default_factory=list)
    category_summary_rows: list[dict[str, Any]] = field(default_factory=list)
    priority_rows: list[dict[str, Any]] = field(default_factory=list)
    analysis_notes_text: str = ''
