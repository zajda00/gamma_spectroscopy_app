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
  notes: "Do zmiany, jeśli inna linia ma być referencyjna."

ground_state_feeding:
  assume_zero_initially: true
  final_assume_zero: false
  estimation_method: "iterative_from_higher_0plus_abf"
  iteration_scheme:
    - step: 1
      description: "W pierwszym kroku założyć brak zasilania do stanu podstawowego 0+."
    - step: 2
      description: "Policzyć apparent beta feeding do wyższych stanów 0+ w 122Cd."
    - step: 3
      description: "Na podstawie zachowania zasileń stanów 0+ oszacować niezerowe zasilanie stanu podstawowego."
    - step: 4
      description: "Wprowadzić oszacowane zasilanie do g.s. i przeliczyć cały bilans intensywności oraz beta feeding."
    - step: 5
      description: "Powtarzać iterację do uzyskania stabilnych wartości."
  initial_ground_state_feeding_percent: 0.0
  dinitial_ground_state_feeding_percent: 0.0
  fitted_ground_state_feeding_percent: null
  dfitted_ground_state_feeding_percent: null
  notes: "Docelowo zasilanie g.s. ma być niezerowe i wyznaczone iteracyjnie."

feeding_balance:
  mode: "level_by_level"
  compute_apparent_beta_feeding: true
  include_ground_state_in_final_balance: true
  use_only_levels_with_defined_incoming_and_outgoing_transitions: true
  notes: "Dokładna lista poziomów będzie pobierana z tabeli leveli i przejść."

logft:
  enabled: true
  mode: "compute_after_iterative_ground_state_feeding"
  use_parent_state_specific_half_lives: true
  compute_for_levels_with_positive_beta_feeding_only: true
  notes: "Log ft liczyć dopiero po ustabilizowaniu iteracyjnego oszacowania zasilania do g.s."

energy_matching:
  use_experimental_energies_as_primary: true
  literature_energies_for_reference_only: true
  matching_tolerance_keV: 0.3
  notes: "Różnice rzędu 0.1 keV między NNDC a eksperymentem nie są problemem dla tego pliku."

spin_parity_inference:
  enable_selection_rule_checks: true
  allowed_parent_states:
    - "1-"
    - "9-"
  excluded_parent_states:
    - "3+"
  notes: "Ocena zgodności feedingów i log ft ma być wykonywana tylko względem stanów 1- i 9-."

data_usage:
  use_relative_intensities: true
  use_corrected_peak_areas: true
  do_not_recalculate_energy_calibration: true
  do_not_recalculate_gamma_efficiency_curve: true
  notes: "Zakładamy, że kalibracje i współczynniki wydajnościowe są dostarczone osobno i nie będą zmieniane."

notes: "Roboczy plik wejściowy dla analizy 122Ag -> 122Cd przy założeniu istnienia wyłącznie stanów 1- i 9- w 122Ag oraz iteracyjnego wyznaczania niezerowego zasilania stanu podstawowego 122Cd."
