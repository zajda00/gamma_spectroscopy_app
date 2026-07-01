from __future__ import annotations
from pathlib import Path
from .models import Level, Transition, BetaInputs

LEVEL_BLOCK_START = '0 %begining value of /lastLevelEndELabel'
LEVEL_BLOCK_END = 'levelEndX 2.3 cm sub %begining (max) value of x coordinate for transitions'
PAGE_MARKER = '%%Page: 1 1'
SHOWPAGE_MARKER = 'showpage'


def _nuclide_to_parts(text: str):
    import re
    m = re.match(r'^(\d+)([A-Za-z]+)$', text.strip())
    if not m:
        return 0, text.strip(), 0
    a = int(m.group(1))
    sym = m.group(2)
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

    def render(self, beta_inputs: BetaInputs, levels: list[Level], transitions: list[Transition], abf_map: dict[str, str] | None = None, logft_map: dict[str, str] | None = None) -> str:
        lines = self.template_text.splitlines()

        parent_a, parent_sym, parent_z = _nuclide_to_parts(beta_inputs.parent_nucleus)
        daughter_a, daughter_sym, daughter_z = _nuclide_to_parts(beta_inputs.daughter_nucleus)

        _replace_line_starting(lines, '/dSym ', f'/dSym ({daughter_sym}) def')
        _replace_line_starting(lines, '/dA ', f'/dA {daughter_a} def')
        _replace_line_starting(lines, '/dZ ', f'/dZ {daughter_z} def')
        _replace_line_starting(lines, '/mSym ', f'/mSym ({parent_sym}) def')
        _replace_line_starting(lines, '/mA ', f'/mA {parent_a} def')
        _replace_line_starting(lines, '/mZ ', f'/mZ {parent_z} def')
        _replace_line_starting(lines, '/mSpinpar ', f'/mSpinpar ((_AUTO_)) def\t\t\t\t%spin and parity of mother nuclues')
        lines = [ln.replace('(_AUTO_)', _fmt_spin_display(beta_inputs)) for ln in lines]
        _replace_line_starting(lines, '/mQ ', f'/mQ ({_fmt_q_display(beta_inputs)}) def\t\t\t%Q value of decays')
        if beta_inputs.neutron_separation_energy_keV is not None:
            _replace_line_starting(lines, '/sNucl ', f'/sNucl ({beta_inputs.neutron_separation_energy_keV:.1f}) def')
            _replace_line_starting(lines, '/sNuclShow ', f'/sNuclShow {1 if beta_inputs.show_neutron_separation else 0} def')

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
