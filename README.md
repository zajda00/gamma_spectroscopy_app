# Decay scheme app

Desktop application in Python and PySide6 for preparing nuclear decay-scheme inputs, editing levels and transitions, computing apparent beta feeding in two modes, generating EPS-like scheme files from a PostScript template, and producing approximate local log ft values with an architecture ready for later integration with IAEA tools and Weisskopf estimators.

## What this first version does

- loads prepared batch files:
  - `beta_inputs_*.md` or `.yaml`
  - `levels_*.csv`
  - `transitions_*.csv`
  - optional uncertain / summary / literature files
- lets you edit experiment settings from a GUI:
  - parent and daughter nuclide
  - Q value and uncertainty
  - mother-state spins and half-lives
  - neutron separation energy and display toggles
- lets you edit level `Jpi` proposals directly in a table
- computes ABF in two modes:
  - no ground-state feeding
  - closure-based ground-state feeding
- computes approximate local log ft values per parent state
- generates a scheme file by preserving the preamble of a user-supplied PostScript/TXT template and replacing only the variable definitions and data block
- writes the updated scheme file continuously when `Auto write EPS` is enabled
- optionally renders a PNG preview if Ghostscript (`gs`) is available on the machine
- keeps the code modular for later integration with Weisskopf and external log ft calculators

## Important scientific note

The included local log ft module is an approximate working implementation for development and triage. It is not a replacement for a fully validated external calculator. The architecture is intentionally split so a later adapter for IAEA / LOGFT / RadiationReport can be dropped in.

## Folder layout

```text
DecaySchemeApp/
  README.md
  requirements.txt
  pyproject.toml
  main.py
  decay_scheme_app/
    models.py
    loaders.py
    beta_inputs.py
    abf.py
    logft.py
    spin_rules.py
    eps_template.py
    preview.py
    main_window.py
    weisskopf_adapter.py
  example_data/
    ... sample CSV / MD files ...
```

## Install

```bash
python -m venv .venv
. .venv/bin/activate  # Linux/macOS
# .venv\Scripts\activate  # Windows
pip install -r requirements.txt
```

## Run

```bash
python main.py
```

## Headless validation

```bash
python main.py --validate ./example_data
```

## Using a scheme template

The app expects a text version of the EPS/PostScript scheme template. It preserves the original preamble and definitions, and rewrites:

- mother/daughter nuclide definitions
- Q value
- mother spin string
- neutron separation-energy settings
- accepted levels block
- accepted transitions block

If the GUI cannot render EPS on your machine, the app can still keep writing the updated file and you can view it in an external EPS/PS/PDF viewer.

## ABF modes

### 1. No ground-state feeding

For each level:

`ABF_raw = sum(outgoing transition intensities) - sum(incoming transition intensities)`

### 2. Closure-based ground-state feeding

- compute excited-state ABF values
- use positive excited-state ABF values to define residual closure to 100%
- assign the residual to the ground state

This is a first working mode. The code is prepared for a later iterative estimator based on higher `0+` states.

## Approximate local log ft

The local module:

- uses parent-state specific half-lives
- computes one value per allowed parent-state hypothesis
- if level spin is missing, it computes all parent possibilities
- if level spin is known, it filters parent states by simple spin-difference logic up to a configurable forbiddenness order

The phase-space factor is intentionally approximate in this first release.

## Future integration points

- `decay_scheme_app.weisskopf_adapter.WeisskopfAdapter`
- `decay_scheme_app.logft.ExternalLogftAdapter`
- `decay_scheme_app.eps_template.EpsTemplateEngine`

## Recommended workflow

1. put batch files in one folder
2. open the app
3. load the folder
4. select the scheme template txt/eps source
5. review settings and level-spin assignments
6. compute ABF and log ft
7. auto-write or save the regenerated scheme file
8. continue development in VS Code
