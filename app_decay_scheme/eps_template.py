from __future__ import annotations
from pathlib import Path
from .models import Level, Transition, BetaInputs, RenderSettings

LEVEL_BLOCK_START = '0 %begining value of /lastLevelEndELabel'
LEVEL_BLOCK_END = 'levelEndX 2.3 cm sub %begining (max) value of x coordinate for transitions'
PAGE_MARKER = '%%Page: 1 1'
SHOWPAGE_MARKER = 'showpage'


def _nuclide_to_parts(text: str):
    """Legacy fallback: parse nucleus from string format (e.g., '122Ag')."""
    import re
    m = re.match(r'^(\d+)([A-Za-z]+)$', text.strip())
    if not m:
        return 0, text.strip(), 0
    a = int(m.group(1))
    sym = m.group(2)
    # Try to use periodic table
    try:
        from .periodic_table import z_from_symbol
        z = z_from_symbol(sym) or 0
    except:
        # Fallback hardcoded map
        z_map = {'Ag': 47, 'Cd': 48, 'In': 49, 'Sn': 50}
        z = z_map.get(sym, 0)
    return a, sym, z


def _fmt_spin_display(beta_inputs: BetaInputs) -> str:
    return ', '.join(s.jpi for s in beta_inputs.parent_states if s.include_in_analysis)


def _fmt_q_display(beta_inputs: BetaInputs) -> str:
    return f"{beta_inputs.qbeta_keV:.0f}({beta_inputs.dqbeta_keV:.0f}) keV"


def _fmt_level_line(level: Level, abf: str, logft: str, dashed: int = 0, color: int = 0) -> str:
    jpi = f"({level.jpi})" if level.jpi else '()'
    e_string = f"({level.e_level_keV:.2f})"
    t12 = '()' if level.t12_s in (None, 0) else f"({level.t12_s})"
    return f"({abf})\t({logft})\t\t\t{e_string}\t\t{jpi}\t\t{level.e_level_keV:.2f}\t{t12}\t\t{dashed}\t{color}\tlevel"


def _fmt_trans_label(tr: Transition) -> str:
    intensity = '' if tr.relative_percent is None else f"{tr.relative_percent:.1f}"
    return f"({tr.e_gamma_keV:.1f} ({intensity}))"


def _fmt_trans_line(tr: Transition, dashed: int = 0, color: int = 0) -> str:
    return f"()\t()\t{_fmt_trans_label(tr)}\t()\t\t\t  {tr.e_level_initial_keV:.2f}\t{tr.e_level_final_keV:.2f}\t\t50\t{dashed}\t{color}\ttrans"


def _replace_line_starting(lines: list[str], prefix: str, replacement: str) -> None:
    for i, line in enumerate(lines):
        if line.lstrip().startswith(prefix):
            indent = line[: len(line) - len(line.lstrip())]
            lines[i] = indent + replacement
            return


def _find_nth(lines: list[str], needle: str, n: int) -> int:
    count = 0
    for i, line in enumerate(lines):
        if needle in line:
            count += 1
            if count == n:
                return i
    raise ValueError(f"Could not find occurrence {n} of marker: {needle}")


def _normalize_template_text(text: str) -> str:
    normalized = []
    for line in text.splitlines():
        if line.startswith('\\%'):
            normalized.append(line[1:])
        else:
            normalized.append(line)
    out = '\n'.join(normalized)
    if not out.endswith('\n'):
        out += '\n'
    return out


class EpsTemplateEngine:
    def __init__(self, template_text: str):
        self.template_text = _normalize_template_text(template_text)

    @classmethod
    def from_file(cls, path: Path):
        return cls(path.read_text(encoding='utf-8'))

    def render(self, beta_inputs: BetaInputs, levels: list[Level], transitions: list[Transition], 
               render_settings: RenderSettings | None = None,
               abf_map: dict[str, str] | None = None, logft_map: dict[str, str] | None = None) -> str:
        if render_settings is None:
            render_settings = RenderSettings()
        
        lines = self.template_text.splitlines()

        # Use nucleus data from beta_inputs if available, otherwise parse from string
        if beta_inputs.daughter_a > 0 and beta_inputs.daughter_z > 0:
            daughter_a = beta_inputs.daughter_a
            daughter_z = beta_inputs.daughter_z
            # Try to get symbol from periodic table
            try:
                from .periodic_table import symbol_from_z
                daughter_sym = symbol_from_z(daughter_z)
            except:
                _, daughter_sym, _ = _nuclide_to_parts(beta_inputs.daughter_nucleus)
        else:
            daughter_a, daughter_sym, daughter_z = _nuclide_to_parts(beta_inputs.daughter_nucleus)
        
        if beta_inputs.mother_a > 0 and beta_inputs.mother_z > 0:
            parent_a = beta_inputs.mother_a
            parent_z = beta_inputs.mother_z
            try:
                from .periodic_table import symbol_from_z
                parent_sym = symbol_from_z(parent_z)
            except:
                _, parent_sym, _ = _nuclide_to_parts(beta_inputs.parent_nucleus)
        else:
            parent_a, parent_sym, parent_z = _nuclide_to_parts(beta_inputs.parent_nucleus)

        # Daughter nucleus
        _replace_line_starting(lines, '/dSym ', f'/dSym ({daughter_sym}) def')
        _replace_line_starting(lines, '/dA ', f'/dA {daughter_a} def')
        _replace_line_starting(lines, '/dZ ', f'/dZ {daughter_z} def')
        daughter_n = daughter_a - daughter_z
        _replace_line_starting(lines, '/dN ', f'/dN {daughter_n} def')
        
        # Mother nucleus
        _replace_line_starting(lines, '/mShow ', f'/mShow {1 if render_settings.mother_show else 0} def')
        _replace_line_starting(lines, '/mSym ', f'/mSym ({parent_sym}) def')
        _replace_line_starting(lines, '/mA ', f'/mA {parent_a} def')
        _replace_line_starting(lines, '/mZ ', f'/mZ {parent_z} def')
        parent_n = parent_a - parent_z
        _replace_line_starting(lines, '/mN ', f'/mN {parent_n} def')
        
        # Decay channel
        _replace_line_starting(lines, '/decay ', f'/decay {beta_inputs.decay_channel} def')
        
        # Mother display toggles
        _replace_line_starting(lines, '/mT12Show ', f'/mT12Show {1 if render_settings.mother_t12_show else 0} def')
        _replace_line_starting(lines, '/mSpinparShow ', f'/mSpinparShow {1 if render_settings.mother_spinpar_show else 0} def')
        _replace_line_starting(lines, '/mQShow ', f'/mQShow {1 if render_settings.mother_q_show else 0} def')
        _replace_line_starting(lines, '/mSnShow ', f'/mSnShow {1 if render_settings.mother_sn_show else 0} def')
        _replace_line_starting(lines, '/mPnShow ', f'/mPnShow {1 if render_settings.mother_pn_show else 0} def')
        
        # Mother values
        if beta_inputs.mother_t12:
            _replace_line_starting(lines, '/mT12 ', f'/mT12 ({beta_inputs.mother_t12}) def')
        
        # Mother spinpar: use explicit value if set, otherwise try auto-format from parent states
        if beta_inputs.mother_spinpar:
            _replace_line_starting(lines, '/mSpinpar ', f'/mSpinpar ({beta_inputs.mother_spinpar}) def')
        elif beta_inputs.parent_states:
            spinpar_str = _fmt_spin_display(beta_inputs)
            if spinpar_str:
                _replace_line_starting(lines, '/mSpinpar ', f'/mSpinpar ({spinpar_str}) def')
        
        # Mother Q value
        if beta_inputs.mother_q:
            _replace_line_starting(lines, '/mQ ', f'/mQ ({beta_inputs.mother_q}) def')
        else:
            q_str = _fmt_q_display(beta_inputs)
            _replace_line_starting(lines, '/mQ ', f'/mQ ({q_str}) def')
        
        # Mother Sn (separation energy neutron)
        if beta_inputs.mother_sn:
            _replace_line_starting(lines, '/mSn ', f'/mSn ({beta_inputs.mother_sn}) def')
        
        # Mother Pn
        if beta_inputs.mother_pn:
            _replace_line_starting(lines, '/mPn ', f'/mPn ({beta_inputs.mother_pn}) def')
        
        # Separation energy
        _replace_line_starting(lines, '/sNuclShow ', f'/sNuclShow {1 if render_settings.separation_energy_show else 0} def')
        _replace_line_starting(lines, '/sNuclType ', f'/sNuclType ({beta_inputs.separation_energy_type}) def')
        if beta_inputs.neutron_separation_energy_keV is not None:
            _replace_line_starting(lines, '/sNucl ', f'/sNucl ({beta_inputs.neutron_separation_energy_keV:.1f}) def')
        
        # Level-side annotations
        _replace_line_starting(lines, '/betapcShow ', f'/betapcShow {1 if render_settings.beta_feeding_show else 0} def')
        _replace_line_starting(lines, '/logftShow ', f'/logftShow {1 if render_settings.logft_show else 0} def')
        _replace_line_starting(lines, '/spinparShow ', f'/spinparShow {1 if render_settings.spinpar_show else 0} def')
        _replace_line_starting(lines, '/t12Show ', f'/t12Show {1 if render_settings.t12_show else 0} def')
        
        # Drawing scales and layout
        _replace_line_starting(lines, '/scaleX ', '/scaleX {0.1 mul} def')
        _replace_line_starting(lines, '/scaleE ', '/scaleE {2.7 mul} def')
        _replace_line_starting(lines, '/fontSize ', f'/fontSize {render_settings.font_size} def')
        _replace_line_starting(lines, '/fontSizeTrans ', f'/fontSizeTrans {render_settings.font_size_trans} def')

        # Rebuild level data block inside preparePage only.
        level_start_idx = _find_nth(lines, LEVEL_BLOCK_START, 1)
        level_end_idx = _find_nth(lines, LEVEL_BLOCK_END, 1)
        level_block = [LEVEL_BLOCK_START, '']
        for level in sorted(levels, key=lambda l: l.e_level_keV):
            abf = (abf_map or {}).get(level.level_id, '')
            logft = (logft_map or {}).get(level.level_id, '')
            level_block.append(_fmt_level_line(level, abf, logft, dashed=0, color=0))
        level_block.append('')
        level_block.append(LEVEL_BLOCK_END)
        lines[level_start_idx: level_end_idx + 1] = level_block

        # Rebuild transition data block only between the second x-start marker and showpage.
        page_idx = _find_nth(lines, PAGE_MARKER, 1)
        trans_start_idx = _find_nth(lines, LEVEL_BLOCK_END, 2)
        showpage_idx = _find_nth(lines, SHOWPAGE_MARKER, 1)
        trans_block = [LEVEL_BLOCK_END, '']
        for tr in sorted(transitions, key=lambda t: (t.e_level_initial_keV or 0.0, t.e_gamma_keV)):
            dashed = 1 if tr.quality_flag in {'doublet_affected', 'estimated_from_doublet_split', 'possible_escape_peak'} else 0
            color = 1 if tr.origin == 'new' else 2 if tr.origin == '2008 work' else 0
            trans_block.append(_fmt_trans_line(tr, dashed=dashed, color=color))
        trans_block.append('')
        trans_block.append(SHOWPAGE_MARKER)
        lines[trans_start_idx: showpage_idx + 1] = trans_block

        out = '\n'.join(lines)
        if not out.endswith('\n'):
            out += '\n'
        if not out.startswith('%!PS-Adobe'):
            raise ValueError('Generated PostScript does not start with a valid EPS header.')
        return out
