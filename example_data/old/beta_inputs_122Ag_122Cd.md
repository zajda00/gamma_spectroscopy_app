parent_nucleus: 122Ag
daughter_nucleus: 122Cd

qbeta_keV: 9510
dqbeta_keV: 40

parent_states:
  - state_id: ag122_1m
    Jpi: "1-"
    excitation_energy_keV: 0.0
    dexcitation_energy_keV: 0.0
    half_life_ms: 550
    dhalf_life_ms: 50
    include_in_analysis: true
    notes: "Przyjęty stan podstawowy 122Ag."

  - state_id: ag122_9m
    Jpi: "9-"
    excitation_energy_keV: 303.7
    dexcitation_energy_keV: 5.0
    half_life_ms: 200
    dhalf_life_ms: 50
    include_in_analysis: true
    notes: "Przyjęty izomer wysoko-spinowy 122Ag."

excluded_parent_states:
  - Jpi: "3+"
    half_life_ms: 529
    dhalf_life_ms: 13
    include_in_analysis: false
    reason: "Stan uznany za nieistniejący, zgodnie z założeniem analizy."

analysis_scope:
  use_only_parent_states:
    - "1-"
    - "9-"
  exclude_parent_states:
    - "3+"

beta_efficiency_treatment: "assume_constant_for_relative_intensities"
gamma_efficiency_already_applied: true
internal_conversion_already_applied: false

normalization:
  mode: "relative_gamma_intensities"
  reference_transition_keV: 569.36
  reference_intensity: 100.0

ground_state_feeding:
  assume_zero_initially: true
  final_assume_zero: false
  estimation_method: "iterative_from_higher_0plus_abf"

feeding_balance:
  mode: "level_by_level"
  compute_apparent_beta_feeding: true
  include_ground_state_in_final_balance: true

logft:
  enabled: true
  mode: "compute_after_iterative_ground_state_feeding"
  use_parent_state_specific_half_lives: true
  compute_for_levels_with_positive_beta_feeding_only: true

energy_matching:
  use_experimental_energies_as_primary: true
  literature_energies_for_reference_only: true
  matching_tolerance_keV: 0.3

spin_parity_inference:
  enable_selection_rule_checks: true
  allowed_parent_states:
    - "1-"
    - "9-"
  excluded_parent_states:
    - "3+"

data_usage:
  use_relative_intensities: true
  use_corrected_peak_areas: true
  do_not_recalculate_energy_calibration: true
  do_not_recalculate_gamma_efficiency_curve: true
