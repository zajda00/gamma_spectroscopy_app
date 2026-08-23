# -*- coding: utf-8 -*-
"""NNDC API client for fetching nuclear data.

Queries NNDC (National Nuclear Data Center) REST APIs to retrieve:
- Q-value data (Beta decay Q-values from NSR)
- Nuclear properties (half-life, spin/parity)
- Separation energies (Sn, Sp, etc.)

Data is fetched from public NNDC APIs with built-in caching to avoid repeated requests.
"""

from __future__ import annotations

import html
import json
import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, Optional
import urllib.error
import urllib.parse
import urllib.request

logger = logging.getLogger(__name__)


class FetchError(Exception):
    """Raised when NNDC API fetch fails."""
    pass


def first_not_none(*values):
    """Return the first value that is not None."""
    for value in values:
        if value is not None:
            return value
    return None


def parse_nds_value_with_uncertainty(raw_value: str) -> tuple[float | None, float | None]:
    """Parse NDS-style value strings like '4.77×10^3 + 4' or '100.42 + 11'.

    Returns (value_keV, uncertainty_keV) for the numeric portion.
    """
    text = (raw_value or '').strip()
    if not text:
        return None, None

    text = text.replace('×', 'x').replace('−', '-')
    text = re.sub(r'\s+', ' ', text)

    sci_match = re.search(
        r'(?P<mant>\d+(?:\.\d+)?)\s*x\s*10\s*(?:\^)?\s*(?P<exp>[+-]?\d+)',
        text,
        flags=re.I,
    )
    if sci_match:
        mantissa = float(sci_match.group('mant'))
        exponent = int(sci_match.group('exp'))
        value = mantissa * (10 ** exponent)
        mantissa_decimals = len(sci_match.group('mant').split('.')[-1]) if '.' in sci_match.group('mant') else 0
        tail = text[sci_match.end():]
        unc_match = re.search(r'(?P<unc>\d+)', tail)
        if unc_match:
            unc_digits = int(unc_match.group('unc'))
            unc = unc_digits * (10 ** (exponent - mantissa_decimals))
            return value, unc
        return value, None

    value_match = re.search(r'(?P<val>\d+(?:\.\d+)?)', text)
    if not value_match:
        return None, None

    value_text = value_match.group('val')
    value = float(value_text)
    decimal_places = len(value_text.split('.')[-1]) if '.' in value_text else 0
    tail = text[value_match.end():]

    unc_match = re.search(r'(?:[±+]\s*|\s+)(?P<unc>\d+(?:\.\d+)?)', tail, flags=re.I)
    if unc_match is None:
        unc_match = re.search(r'(?P<unc>\d+(?:\.\d+)?)\s*(?:$|[A-Za-z%µ])', tail, flags=re.I)
    if unc_match:
        unc = float(unc_match.group('unc')) * (10 ** (-decimal_places))
        return value, unc
    return value, None


def _normalize_display_number(value: float | None, digits: int = 3) -> str:
    """Return a compact display string for numeric values without losing precision."""
    if value is None:
        return ''
    if isinstance(value, int):
        return str(value)
    if abs(value) >= 1000 or (abs(value) >= 1 and value % 1 == 0):
        return f"{value:.3g}"
    return f"{value:.12g}".rstrip('0').rstrip('.') if '.' in f"{value:.12g}" else f"{value:.12g}"


def _parse_energy_field(raw_text: str) -> tuple[float | None, float | None, str, str]:
    """Parse energy text from NNDC adopted levels.

    Returns (energy_keV, energy_uncertainty_keV, energy_display, energy_uncertainty_display).
    """
    text = (raw_text or '').strip()
    if not text:
        return None, None, '', ''

    clean = html.unescape(text).replace('&nbsp;', ' ')
    clean = re.sub(r'<[^>]+>', ' ', clean)
    clean = re.sub(r'\s+', ' ', clean).strip()

    if re.fullmatch(r'\d+(?:\.\d+)?\s*\+\s*[A-Za-z]+', clean, flags=re.I):
        return None, None, clean, ''

    if re.fullmatch(r'\d+(?:\.\d+)?\s*\+\s*X', clean, flags=re.I):
        return None, None, clean, ''

    pair_match = re.search(r'^(?P<base>\d+(?:\.\d+)?)\s+(?P<unc>\d+(?:\.\d+)?)\s*$', clean)
    if pair_match:
        raw_value = float(pair_match.group('base'))
        raw_unc = float(pair_match.group('unc'))
        return raw_value, raw_unc, pair_match.group('base'), pair_match.group('unc')

    value_match = re.search(r'(?P<base>\d+(?:\.\d+)?)', clean)
    if not value_match:
        return None, None, clean, ''

    raw_value = float(value_match.group('base'))
    if re.search(r'\+\s*[A-Za-z]', clean, flags=re.I):
        return None, None, clean, ''

    if re.search(r'\(|±|\+/-', clean):
        parsed_value, parsed_unc = parse_nds_value_with_uncertainty(clean)
        if parsed_value is not None:
            return parsed_value, parsed_unc, _normalize_display_number(parsed_value), _normalize_display_number(parsed_unc)

    return raw_value, None, str(raw_value), ''

def _parse_half_life_field(
    raw_text: str,
) -> tuple[str, float | None, float | None, str, str]:
    """
    Parse NuDat / NDS half-life strings.

    Examples:
    - "0.529 s 13"   -> "0.529 s", 0.529 s, 0.013 s, "0.013 s"
    - "0.55 s 5"     -> "0.55 s", 0.55 s, 0.05 s, "0.05 s"
    - "37.230 m 14"  -> "37.230 m", 2233.8 s, 0.84 s, "0.014 m"
    - "715 ms 3"     -> "715 ms", 0.715 s, 0.003 s, "3 ms"

    Returns:
        (
            half_life_display,
            half_life_seconds,
            half_life_uncertainty_seconds,
            half_life_uncertainty_display,
            raw_half_life_value_display,
        )
    """
    text = (raw_text or "").strip()

    if not text:
        return "", None, None, "", ""

    clean = html.unescape(text).replace("&nbsp;", " ")
    clean = re.sub(r"<[^>]+>", " ", clean)
    clean = re.sub(r"\s+", " ", clean).strip()

    # Longer units must occur before "m".
    # Otherwise "ms" could be incorrectly read as "m".
    match = re.search(
        r"(?P<value>\d+(?:\.\d+)?)\s*"
        r"(?P<unit>min|ms|µs|μs|us|ns|ps|yr|y|h|d|m|s)?"
        r"(?:"
        r"\s*\(\s*(?P<unc_paren>\d+(?:\.\d+)?)\s*\)"
        r"|\s+(?P<unc_plain>\d+(?:\.\d+)?)"
        r")?",
        clean,
        flags=re.I,
    )

    if not match:
        return clean, None, None, "", clean

    value_text = match.group("value")
    value = float(value_text)

    # Keep the original unit for GUI display.
    unit_display = match.group("unit") or "s"

    # Normalize only for conversion to seconds.
    unit_key = unit_display.lower().replace("μ", "µ")

    unit_map = {
        "ps": 1e-12,
        "ns": 1e-9,
        "us": 1e-6,
        "µs": 1e-6,
        "ms": 1e-3,
        "s": 1.0,
        "m": 60.0,
        "min": 60.0,
        "h": 3600.0,
        "d": 86400.0,
        "y": 31557600.0,
        "yr": 31557600.0,
    }

    factor = unit_map.get(unit_key, 1.0)
    value_seconds = value * factor

    uncertainty_text = (
        match.group("unc_paren")
        or match.group("unc_plain")
    )

    uncertainty_input_unit = None
    uncertainty_seconds = None

    if uncertainty_text is not None:
        decimal_places = (
            len(value_text.split(".")[1])
            if "." in value_text
            else 0
        )

        # NDS integer uncertainty digits, e.g.:
        # 37.230 m 14 -> 0.014 m
        # 715 ms 3    -> 3 ms
        if "." in uncertainty_text:
            uncertainty_input_unit = float(uncertainty_text)
        else:
            uncertainty_input_unit = (
                float(uncertainty_text)
                * (10 ** (-decimal_places))
            )

        uncertainty_seconds = uncertainty_input_unit * factor

    display_value = f"{value_text} {unit_display}"

    display_uncertainty = ""
    if uncertainty_input_unit is not None:
        display_uncertainty = (
            f"{uncertainty_input_unit:.12g} {unit_display}"
        )

    return (
        display_value,
        value_seconds,
        uncertainty_seconds,
        display_uncertainty,
        clean,
    )


@dataclass
class NNDCData:
    """Container for NNDC nuclear data."""
    # Q-value data
    q_beta_keV: Optional[float] = None
    dq_beta_keV: Optional[float] = None
    q_beta_str: Optional[str] = None

    # Mother/Parent nucleus data
    mother_half_life_str: Optional[str] = None
    mother_spin_parity_str: Optional[str] = None
    mother_q_str: Optional[str] = None
    mother_sn_keV: Optional[float] = None
    mother_sn_str: Optional[str] = None
    mother_pn_keV: Optional[float] = None
    mother_pn_str: Optional[str] = None
    mother_states: list[dict[str, Any]] = field(default_factory=list)

    # Separation energies
    sn_keV: Optional[float] = None
    sn_str: Optional[str] = None
    dsn_keV: Optional[float] = None
    sp_keV: Optional[float] = None
    sp_str: Optional[str] = None
    dsp_keV: Optional[float] = None

    # Metadata
    source: str = ''
    fetch_timestamp: Optional[datetime] = None
    error_message: Optional[str] = None


class NNDCClient:
    """Client for querying NNDC databases.

    Provides methods to fetch Q-values, nuclear properties, and separation energies.
    Implements caching to reduce API load.
    """

    # NNDC REST API endpoints
    NNDC_API_BASE = "https://www.nndc.bnl.gov/nudat3/api/"
    DATASET_PAGE_BASE = "https://www.nndc.bnl.gov/nudat3/getdataset.jsp"

    # Cache settings (in memory)
    CACHE_DURATION = timedelta(hours=24)

    def __init__(self, use_cache: bool = True):
        """Initialize NNDC client.

        Args:
            use_cache: Enable in-memory caching of results
        """
        self.use_cache = use_cache
        self._cache: Dict[str, tuple[NNDCData, datetime]] = {}

    def _cache_key(self, parent: str, daughter: str, decay_mode: str) -> str:
        """Generate cache key for a query."""
        return f"{parent}→{daughter}:{decay_mode}".lower()

    def _get_cached(self, key: str) -> Optional[NNDCData]:
        """Retrieve cached data if available and not expired."""
        if not self.use_cache or key not in self._cache:
            return None

        data, timestamp = self._cache[key]
        if datetime.now() - timestamp > self.CACHE_DURATION:
            del self._cache[key]
            return None

        return data

    def _set_cached(self, key: str, data: NNDCData) -> None:
        """Store data in cache."""
        if self.use_cache:
            self._cache[key] = (data, datetime.now())

    def _query_nudat3_dataset(self, nucleus: str) -> Optional[str]:
        """Fetch the plain HTML dataset page used by NNDC for adopted levels.

        This is a simple fallback for nuclei where the NNDC JSON APIs return no data.
        We then scrape the relevant values directly from the dataset page.
        """
        normalized = nucleus.strip()
        if not normalized:
            return None

        url = f"{self.DATASET_PAGE_BASE}?nucleus={urllib.parse.quote(normalized)}&unc=NDS"
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'gamma-spectroscopy-app/1.0'})
            with urllib.request.urlopen(req, timeout=15) as response:
                text = response.read().decode('utf-8', 'replace')
                if 'List of levels for' in text or 'ADOPTED LEVELS' in text:
                    return text
                return None
        except urllib.error.HTTPError as e:
            if e.code == 404:
                logger.info(f"Dataset page for {nucleus} not found in NNDC")
                return None
            raise FetchError(f"HTTP {e.code}: {e.reason}")
        except urllib.error.URLError as e:
            raise FetchError(f"Network error: {e.reason}")
        except Exception as e:
            raise FetchError(f"Dataset fetch error: {str(e)}")

    def _parse_dataset_html(self, html_text: str, result: NNDCData) -> NNDCData:
        """Parse ADOPTED LEVELS table rows from the NuDat HTML dataset page."""
        try:
            html_src = html_text
            text = re.sub(r'<script.*?</script>', ' ', html_src, flags=re.S | re.I)
            text = re.sub(r'<style.*?</style>', ' ', text, flags=re.S | re.I)
            text = re.sub(r'<[^>]+>', ' ', text)
            text = html.unescape(text)
            text = re.sub(r'\s+', ' ', text)

            def parse_value_with_unc(s: str):
                s = s.strip()
                sci = re.search(r'([0-9]+(?:\.[0-9]+)?)\s*(?:×|x)\s*10\s*\^\s*([+-]?\d+)', s)
                par = re.search(r'([0-9]+(?:\.[0-9]+)?)\s*\(\s*(\d+)\s*\)', s)
                plusminus = re.search(r'([0-9]+(?:\.[0-9]+)?)\s*(?:±|\+/-)\s*([0-9]+(?:\.[0-9]+)?)', s)
                basic = re.search(r'([0-9]+(?:\.[0-9]+)?)', s)
                if sci:
                    base = float(sci.group(1)) * (10 ** int(sci.group(2)))
                    return base, None
                if par:
                    base_str = par.group(1)
                    par_digits = par.group(2)
                    base = float(base_str)
                    if '.' in base_str:
                        decimals = len(base_str.split('.')[-1])
                        unc = int(par_digits) * (10 ** (-decimals))
                    else:
                        unc = int(par_digits)
                    return base, unc
                if plusminus:
                    base = float(plusminus.group(1))
                    unc = float(plusminus.group(2))
                    return base, unc
                if basic:
                    return float(basic.group(1)), None
                return None, None

            q_match = re.search(r'Q\s*\(β-\)\s*=\s*([0-9.]+)\s*(?:×\s*10\s*\^?\s*([0-9]+))?\s*keV(?:\s*(\d+))?', text, flags=re.I)
            if q_match:
                base = float(q_match.group(1))
                exp = int(q_match.group(2) or 0)
                q_val = base * (10 ** exp)
                result.q_beta_keV = q_val
                result.q_beta_str = f"{q_val:.0f} keV"
                result.mother_q_str = f"{q_val:.0f} keV"
                unc_digit = q_match.group(3)
                if unc_digit:
                    dq_val = float(unc_digit) * (10 ** (exp - 2))
                    result.dq_beta_keV = dq_val
                    result.q_beta_str = f"{q_val:.0f}({dq_val:.0f}) keV"
                    result.mother_q_str = result.q_beta_str

            def _extract_separation_value(label: str) -> tuple[float | None, float | None]:
                pattern = rf'S\s*\(\s*{label}\s*\)\s*=\s*(.*?)(?=\s*S\s*\(\s*(?:n|p)\s*\)|\s*Q\s*\(|\s*$)'
                match = re.search(pattern, text, flags=re.I | re.S)
                if not match:
                    return None, None
                return parse_nds_value_with_uncertainty(match.group(1))

            sn_value, sn_unc = _extract_separation_value('n')
            if sn_value is not None:
                result.sn_keV = sn_value
                result.sn_str = f"{sn_value:.2f} keV"
                result.mother_sn_keV = sn_value
                result.mother_sn_str = result.sn_str
                if sn_unc is not None:
                    result.dsn_keV = sn_unc
                    result.mother_sn_str = f"{sn_value:.2f}({sn_unc:.2f}) keV"

            sp_value, sp_unc = _extract_separation_value('p')
            if sp_value is not None:
                result.sp_keV = sp_value
                result.sp_str = f"{sp_value:.2f} keV"
                if sp_unc is not None:
                    result.dsp_keV = sp_unc

            def clean_cell_value(raw_html: str) -> str:
                cleaned = re.sub(r'onmouseover=".*?"', ' ', raw_html, flags=re.S | re.I)
                cleaned = re.sub(r'onmouseout=".*?"', ' ', cleaned, flags=re.S | re.I)
                cleaned = re.sub(r'<[^>]+>', ' ', html.unescape(cleaned))
                cleaned = cleaned.replace('&nbsp;', ' ')
                cleaned = re.sub(r'\s+', ' ', cleaned).strip()
                return cleaned

            adopted_tables = []
            for table_match in re.finditer(r'<table\b[^>]*>(.*?)</table>', html_src, flags=re.S | re.I):
                table_html = table_match.group(1)
                if 'cell elvl' in table_html and 'cellc jpi' in table_html and 'cellc t12' in table_html:
                    adopted_tables.append(table_html)

            decay_branch_pattern = re.compile(
                r'(%\s*(?:β|BETA|EC|ε|IT|α|ALPHA|SF)|'
                r'\b(?:β[-+]?|EC|ε|IT|α|SF)\b)',
                flags=re.I,
            )

            def is_ground_state(
                energy_keV: float | None,
                energy_display: str,
            ) -> bool:
                """
                Stan podstawowy ma liczbową energię równą zero.
                Wpisy typu 0.0+X nie są automatycznie stanem podstawowym.
                """
                if energy_keV is None:
                    return False

                return (
                    abs(energy_keV) < 1e-12
                    and '+' not in (energy_display or '')
                )

            def has_explicit_decay_branch(t12_decay_raw: str) -> bool:
                """
                Zwraca True tylko dla rekordów zawierających jawny tryb
                rozpadu, np. % beta-, IT, EC, alpha lub SF.
                """
                return bool(
                    t12_decay_raw
                    and decay_branch_pattern.search(t12_decay_raw)
                )


            state_entries: list[dict[str, Any]] = []
            for table_html in adopted_tables:
                for match in re.finditer(
                    r'<td\b[^>]*class=[\'\"]?[^\'\"]*elvl[^\'\"]*[\'\"]?[^>]*>(.*?)</td>\s*'
                    r'<td\b[^>]*class=[\'\"]?[^\'\"]*jpi[^\'\"]*[\'\"]?[^>]*>(.*?)</td>\s*'
                    r'<td\b[^>]*class=[\'\"]?[^\'\"]*t12[^\'"]*[\'"]?[^>]*>(.*?)</td>',
                    table_html,
                    flags=re.S | re.I,
                ):
                    energy_html, jpi_html, t12_html = match.groups()
                    energy_txt = clean_cell_value(energy_html)
                    jpi_txt = clean_cell_value(jpi_html)
                    t12_txt = clean_cell_value(t12_html)
                    t12_decay_raw = t12_txt
                    if not energy_txt or not jpi_txt or not t12_txt:
                        continue

                    energy_keV, energy_uncertainty_keV, energy_display, energy_uncertainty_display = _parse_energy_field(energy_txt)
                    half_life_display, half_life_seconds, half_life_uncertainty_seconds, half_life_uncertainty_display, _ = _parse_half_life_field(t12_txt)
                    jpi_raw = jpi_txt.strip()
                    if not jpi_raw:
                        continue

                    entry: dict[str, Any] = {
                        'jpi_raw': jpi_raw,
                        'jpi': jpi_raw,
                        'energy_display': energy_display,
                        'energy_keV': energy_keV,
                        'energy_uncertainty_keV': energy_uncertainty_keV,
                        'half_life_display': half_life_display,
                        'half_life_uncertainty_display': half_life_uncertainty_display,
                        'e_keV': None if energy_keV is None else str(energy_keV),
                        'de_keV': None if energy_uncertainty_keV is None else str(energy_uncertainty_keV),
                        't12': half_life_display,
                        'dt12': half_life_uncertainty_display,
                        't12_decay_raw': t12_decay_raw,
                        'has_decay_branch': has_explicit_decay_branch(t12_decay_raw),

                    }
                    if energy_display:
                        entry['energy'] = energy_display
                    if energy_uncertainty_display:
                        entry['energy_uncertainty'] = energy_uncertainty_display
                    if half_life_display:
                        entry['half_life'] = half_life_display
                    if half_life_uncertainty_display:
                        entry['half_life_uncertainty'] = half_life_uncertainty_display
                    if half_life_seconds is not None:
                        entry['half_life_seconds'] = half_life_seconds
                    if half_life_uncertainty_seconds is not None:
                        entry['half_life_uncertainty_seconds'] = half_life_uncertainty_seconds
                    if (is_ground_state(energy_keV, energy_display) or entry['has_decay_branch']):
                        state_entries.append(entry)


            if state_entries:
                dedup: dict[str, dict[str, Any]] = {}

                def score(ent: dict[str, Any]) -> int:
                    s = 0
                    if ent.get('energy_keV') is not None:
                        s += 2
                    if ent.get('t12'):
                        s += 1
                    return s

                for ent in state_entries:
                    jraw = ent.get('jpi_raw', '') or ''
                    jnorm = re.sub(r'\s+', '', jraw)
                    if not jnorm:
                        jnorm = f'__nojp__{len(dedup)}'
                    if jnorm not in dedup or score(ent) > score(dedup[jnorm]):
                        dedup[jnorm] = ent

                result.mother_states = list(dedup.values())
                if len(result.mother_states) == 1:
                    result.mother_half_life_str = result.mother_states[0].get('t12', result.mother_half_life_str)
                    result.mother_spin_parity_str = result.mother_states[0].get('jpi', result.mother_spin_parity_str)
                else:
                    if not result.mother_half_life_str:
                        result.mother_half_life_str = '; '.join(e.get('t12', '') for e in result.mother_states if e.get('t12')) or result.mother_half_life_str
                    if not result.mother_spin_parity_str:
                        result.mother_spin_parity_str = '; '.join(e.get('jpi', '') for e in result.mother_states if e.get('jpi')) or result.mother_spin_parity_str
            else:
                plain_state_pattern = re.compile(
                    r'(?P<half>\d+(?:\.\d+)?)\s*(?P<unit>min|ms|µs|us|ns|ps|yr|y|h|d|m|s)?\s*(?:\(\s*(?P<unc>\d+)\s*\))?\s*(?P<jpi>\(\s*[0-9]+(?:/\d+)?\s*[+-]\s*\))',
                    flags=re.I,
                )
                for match in plain_state_pattern.finditer(text):
                    half = match.group('half')
                    unit = (match.group('unit') or 's').lower()
                    jpi = match.group('jpi').strip()
                    entry = {'jpi_raw': jpi, 'jpi': jpi, 't12': f'{half} {unit}' if unit else half}
                    result.mother_states.append(entry)
                if result.mother_states:
                    result.mother_half_life_str = '; '.join(e.get('t12', '') for e in result.mother_states if e.get('t12'))
                    result.mother_spin_parity_str = '; '.join(e.get('jpi', '') for e in result.mother_states if e.get('jpi'))
        except (KeyError, TypeError, ValueError, re.error) as exc:
            logger.warning(f"Error parsing NNDC dataset HTML: {exc}")

        return result

    def fetch_q_value(self, parent_nucleus: str, daughter_nucleus: str, decay_mode: str = "beta-") -> NNDCData:
        """Fetch Q-value for beta decay from NNDC NSR database."""
        cache_key = self._cache_key(parent_nucleus, daughter_nucleus, decay_mode)
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached Q-value for {parent_nucleus}→{daughter_nucleus}")
            return cached

        result = NNDCData(source="NNDC NSR")

        try:
            dataset_html = self._query_nudat3_dataset(parent_nucleus)
            if dataset_html:
                result = self._parse_dataset_html(dataset_html, result)
                result.fetch_timestamp = datetime.now()
                self._set_cached(cache_key, result)
                return result

            logger.warning(f"No Q-value data found for {parent_nucleus}→{daughter_nucleus} on dataset page")
            result.error_message = f"No data available for {parent_nucleus} on dataset page"
        except FetchError as e:
            result.error_message = str(e)
            logger.error(f"Failed to fetch Q-value: {e}")
        except Exception as e:
            result.error_message = f"Unexpected error: {str(e)}"
            logger.error(f"Unexpected error fetching Q-value: {e}", exc_info=True)

        result.fetch_timestamp = datetime.now()
        return result

    def fetch_separation_energies(self, nucleus: str) -> NNDCData:
        """Fetch separation energies (Sn, Sp) for a nucleus."""
        cache_key = f"sep_energy:{nucleus.lower()}"
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached separation energies for {nucleus}")
            return cached

        result = NNDCData(source="NNDC Nudat3")

        try:
            dataset_html = self._query_nudat3_dataset(nucleus)
            if dataset_html:
                result = self._parse_dataset_html(dataset_html, result)
                result.fetch_timestamp = datetime.now()
                self._set_cached(cache_key, result)
                return result

            logger.warning(f"No separation energy data found for {nucleus} on dataset page")
            result.error_message = f"No data available for {nucleus} on dataset page"
        except FetchError as e:
            result.error_message = str(e)
            logger.error(f"Failed to fetch separation energies: {e}")
        except Exception as e:
            result.error_message = f"Unexpected error: {str(e)}"
            logger.error(f"Unexpected error fetching separation energies: {e}", exc_info=True)

        result.fetch_timestamp = datetime.now()
        return result

    def fetch_nuclear_properties(self, nucleus: str) -> NNDCData:
        """Fetch nuclear properties (half-life, spin/parity) for a nucleus."""
        cache_key = f"properties:{nucleus.lower()}"
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached properties for {nucleus}")
            return cached

        result = NNDCData(source="NNDC Nudat3")

        try:
            dataset_html = self._query_nudat3_dataset(nucleus)
            if dataset_html:
                result = self._parse_dataset_html(dataset_html, result)
                result.fetch_timestamp = datetime.now()
                self._set_cached(cache_key, result)
                return result

            logger.warning(f"No nuclear property data found for {nucleus} on dataset page")
            result.error_message = f"No data available for {nucleus} on dataset page"
        except FetchError as e:
            result.error_message = str(e)
            logger.error(f"Failed to fetch nuclear properties: {e}")
        except Exception as e:
            result.error_message = f"Unexpected error: {str(e)}"
            logger.error(f"Unexpected error fetching nuclear properties: {e}", exc_info=True)

        result.fetch_timestamp = datetime.now()
        return result

    def _normalize_nucleus_id(self, nucleus: str) -> str:
        """Convert nucleus symbol (e.g., '122Ag') to NNDC format."""
        nucleus = nucleus.replace('-', '').strip()
        match = re.match(r'^(\d+)([a-zA-Z]+)$', nucleus)
        if match:
            return f"{match.group(1)}{match.group(2).upper()}"

        match = re.match(r'^([a-zA-Z]+)(\d+)$', nucleus)
        if match:
            return f"{match.group(2)}{match.group(1).upper()}"

        return nucleus.upper()

    def _query_nudat3(self, nucleus_id: str) -> Optional[Dict[str, Any]]:
        """Query NNDC Nudat3 database for a nucleus."""
        url = f"{self.NNDC_API_BASE}nucleus/{nucleus_id}/"

        try:
            logger.debug(f"Querying NNDC: {url}")
            req = urllib.request.Request(url)
            req.add_header('User-Agent', 'gamma-spectroscopy-app/1.0')
            with urllib.request.urlopen(req, timeout=10) as response:
                content = response.read().decode('utf-8')
                return json.loads(content)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                logger.info(f"Nucleus {nucleus_id} not found in NNDC")
                return None
            raise FetchError(f"HTTP {e.code}: {e.reason}")
        except urllib.error.URLError as e:
            raise FetchError(f"Network error: {e.reason}")
        except json.JSONDecodeError as e:
            raise FetchError(f"Invalid JSON response: {e}")
        except Exception as e:
            raise FetchError(f"Unexpected error: {str(e)}")

    def _parse_nudat3_response(self, data: Dict[str, Any], result: NNDCData) -> NNDCData:
        """Parse Nudat3 API response for Q-value data."""
        try:
            if isinstance(data, dict):
                if 'decay_schemes' in data:
                    schemes = data['decay_schemes']
                    if isinstance(schemes, list) and schemes:
                        scheme = schemes[0]
                        if 'q_value' in scheme:
                            q_val = scheme['q_value']
                            if isinstance(q_val, (int, float)):
                                result.q_beta_keV = float(q_val)
                                result.q_beta_str = f"{q_val:.1f} keV"

                if 'half_life' in data:
                    hl = data['half_life']
                    if hl:
                        result.mother_half_life_str = str(hl)

                if 'ground_state' in data:
                    gs = data['ground_state']
                    if isinstance(gs, dict):
                        if 'spin_parity' in gs:
                            result.mother_spin_parity_str = gs['spin_parity']
                        if 'half_life' in gs and not result.mother_half_life_str:
                            result.mother_half_life_str = str(gs['half_life'])

                if 'levels' in data and isinstance(data['levels'], list):
                    state_entries: list[dict[str, Any]] = []
                    for lvl in data['levels']:
                        try:
                            jpi = lvl.get('spin_parity') or lvl.get('jpi')
                            hl = lvl.get('half_life') or lvl.get('t1/2')
                            energy = lvl.get('energy') or lvl.get('energy_keV') or lvl.get('e_keV')
                            de = lvl.get('energy_uncertainty') or lvl.get('dE') or lvl.get('de_keV')
                            entry: dict[str, Any] = {}
                            if jpi:
                                entry['jpi_raw'] = str(jpi)
                                entry['jpi'] = str(jpi)
                            if hl:
                                entry['t12'] = str(hl)
                            if energy is not None:
                                entry['energy_keV'] = str(energy)
                            if de is not None:
                                entry['energy_uncertainty_keV'] = str(de)
                            if entry:
                                state_entries.append(entry)
                        except Exception:
                            continue
                    if state_entries:
                        result.mother_states = state_entries
                        if len(state_entries) == 1:
                            result.mother_half_life_str = state_entries[0].get('t12', result.mother_half_life_str)
                            result.mother_spin_parity_str = state_entries[0].get('jpi', result.mother_spin_parity_str)
                        else:
                            if not result.mother_half_life_str:
                                result.mother_half_life_str = '; '.join(e.get('t12', '') for e in state_entries if e.get('t12')) or result.mother_half_life_str
                            if not result.mother_spin_parity_str:
                                result.mother_spin_parity_str = '; '.join(e.get('jpi', '') for e in state_entries if e.get('jpi')) or result.mother_spin_parity_str
        except (KeyError, TypeError, ValueError) as e:
            logger.warning(f"Error parsing Nudat3 response: {e}")

        return result

    def _parse_separation_energies(self, data: Dict[str, Any], result: NNDCData) -> NNDCData:
        """Parse separation energy data from NNDC response."""
        try:
            if isinstance(data, dict):
                if 'separation_energy' in data:
                    sep_data = data['separation_energy']
                    if isinstance(sep_data, dict):
                        if 'sn' in sep_data:
                            result.sn_keV = float(sep_data['sn'])
                            result.sn_str = f"{sep_data['sn']:.1f} keV"
                            result.dsn_keV = None
                        if 'sp' in sep_data:
                            result.sp_keV = float(sep_data['sp'])
                            result.sp_str = f"{sep_data['sp']:.1f} keV"
                            result.dsp_keV = None
        except (KeyError, TypeError, ValueError) as e:
            logger.warning(f"Error parsing separation energies: {e}")

        return result

    def _parse_nuclear_properties(self, data: Dict[str, Any], result: NNDCData) -> NNDCData:
        """Parse nuclear properties (T1/2, spin/parity) from NNDC response."""
        try:
            if isinstance(data, dict):
                if 'ground_state' in data:
                    gs = data['ground_state']
                    if isinstance(gs, dict):
                        if 'half_life' in gs:
                            result.mother_half_life_str = gs['half_life']
                        if 'spin_parity' in gs:
                            result.mother_spin_parity_str = gs['spin_parity']
                if not result.mother_half_life_str and 'half_life' in data:
                    result.mother_half_life_str = data['half_life']
                if not result.mother_spin_parity_str and 'spin_parity' in data:
                    result.mother_spin_parity_str = data['spin_parity']
        except (KeyError, TypeError, ValueError) as e:
            logger.warning(f"Error parsing nuclear properties: {e}")

        return result

    def clear_cache(self) -> None:
        """Clear all cached data."""
        self._cache.clear()
        logger.debug("NNDC cache cleared")
