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
    neutron_separation_energy_uncertainty_keV: float | None = None
    show_neutron_separation: bool = True
    ground_state_strategy: str = 'closure_to_100'
    normalization_reference_keV: float | None = None
    # Simplified project-level settings for apparent beta feeding / gs feeding.
    ground_state_feeding_mode: str = 'closure_to_100'
    manual_ground_state_feeding_percent: float | None = None
    iterative_ground_state_feeding: bool = False
    iterative_tolerance: float = 0.01
    iterative_max_iterations: int = 20
    # Extended nucleus data for mother
    mother_a: int = 0
    mother_z: int = 0
    mother_n: int = 0
    # Extended nucleus data for daughter
    daughter_a: int = 0
    daughter_z: int = 0
    daughter_n: int = 0
    # Additional mother display values
    mother_t12: str = ''  # e.g., "0.72(10) s"
    mother_spinpar: str = ''  # e.g., "(1-), (9-)"
    mother_q: str = ''  # e.g., "9510(40) keV"
    mother_sn: str = ''  # separation energy neutron
    mother_pn: str = ''  # other nuclear parameter
    mother_states: list[dict[str, str]] = field(default_factory=list)
    # Decay channel (0=blank, 1=alpha, 2=beta-, 3=beta+, 4=beta-n)
    decay_channel: int = 2
    # Separation energy type: 'n' or 'p'
    separation_energy_type: str = 'n'

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
    level_origin: str = ''
    certain: bool = True
    # Calculated values (populated after ABF/logft calculation)
    abf: float | None = None
    logft: float | None = None

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
class RenderSettings:
    """Settings for rendering the EPS scheme diagram."""
    # Mother nucleus display
    mother_show: bool = True
    mother_t12_show: bool = True
    mother_spinpar_show: bool = True
    mother_q_show: bool = True
    mother_sn_show: bool = False
    mother_pn_show: bool = False
    # Separation energy
    separation_energy_show: bool = False
    # Level-side annotations
    beta_feeding_show: bool = True
    logft_show: bool = True
    spinpar_show: bool = True
    t12_show: bool = True
    # Drawing scales and layout
    scale_x: float = 0.1
    scale_e: float = 2.7
    font_size: int = 15
    font_size_trans: int = 12
    # PNG output settings
    png_white_background: bool = True

@dataclass
class ProjectData:
    root: Path
    beta_inputs: BetaInputs
    levels: list[Level]
    transitions: list[Transition]
    render_settings: RenderSettings = field(default_factory=RenderSettings)
    uncertain_rows: list[dict[str, Any]] = field(default_factory=list)
    literature_rows: list[dict[str, Any]] = field(default_factory=list)
    category_summary_rows: list[dict[str, Any]] = field(default_factory=list)
    priority_rows: list[dict[str, Any]] = field(default_factory=list)
    analysis_notes_text: str = ''
