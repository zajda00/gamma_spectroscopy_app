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
    mother_states: list[dict[str, str]] = field(default_factory=list)
    
    # Separation energies
    sn_keV: Optional[float] = None
    sn_str: Optional[str] = None
    sp_keV: Optional[float] = None
    sp_str: Optional[str] = None
    # Separation energy uncertainties (keV)
    sn_uncertainty_keV: Optional[float] = None
    sp_uncertainty_keV: Optional[float] = None
    
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
        """Scrape mother Q-value, Sn, Pn, T1/2, spin/parity and excited-level table from NNDC dataset HTML.

        This parser focuses on the dataset page table rows (<tr> tags). It only treats
        an excited level as an isomeric state if the row lists a decay channel (e.g. 'β-').
        It extracts energy (keV) with uncertainty, and half-life with uncertainty and
        converts units (half-life -> seconds) so the UI can display consistent units.
        """
        try:
            # Keep original HTML for table-row-level parsing
            html_src = html_text

            # Prepare a plain-text copy for scalar matches (Q, Sn, Sp) as fallback
            text = re.sub(r'<script.*?</script>', ' ', html_src, flags=re.S | re.I)
            text = re.sub(r'<style.*?</style>', ' ', text, flags=re.S | re.I)
            text = re.sub(r'<[^>]+>', ' ', text)
            text = html.unescape(text)
            text = re.sub(r'\s+', ' ', text)

            # Helper: parse numeric value with uncertainty in parentheses or ± form
            def parse_value_with_unc(s: str):
                s = s.strip()
                # scientific notation like 1.23×10^3 or 1.23 x 10^3
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

            # Q-value (beta) extraction (fallback) — preserve original NNDC handling for parenthesis uncertainty
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
                    # Uncertainty given as last digits in parentheses — scale according to exponent
                    dq_val = float(unc_digit) * (10 ** (exp - 2))
                    result.dq_beta_keV = dq_val
                    result.q_beta_str = f"{q_val:.0f}({dq_val:.0f}) keV"
                    result.mother_q_str = result.q_beta_str

            # Separation energies (Sn, Sp) with uncertainties if present
            sn_match = re.search(r'S\s*\(\s*n\s*\)\s*=\s*([0-9.\(\)×x\^±+\-\s]+)\s*keV', text, flags=re.I)
            if sn_match:
                val_str = sn_match.group(1)
                base, unc = parse_value_with_unc(val_str)
                if base is not None:
                    result.sn_keV = base
                    result.sn_str = f"{base:.2f} keV"
                    result.mother_sn_keV = base
                    result.mother_sn_str = result.sn_str
                    if unc is not None:
                        # treat uncertainty units same as base (keV)
                        result.sn_uncertainty_keV = unc
                        result.mother_sn_str = f"{base:.2f}({unc:.2f}) keV"

            sp_match = re.search(r'S\s*\(\s*p\s*\)\s*=\s*([0-9.\(\)×x\^±+\-\s]+)\s*keV', text, flags=re.I)
            if sp_match:
                val_str = sp_match.group(1)
                base, unc = parse_value_with_unc(val_str)
                if base is not None:
                    result.sp_keV = base
                    result.sp_str = f"{base:.2f} keV"

            # Now parse table rows from the HTML. We look for <tr>..</tr> blocks and inspect each row's text.
            rows = re.findall(r'<tr[^>]*>(.*?)</tr>', html_src, flags=re.S | re.I)
            state_entries: list[dict[str, str]] = []
            for row_html in rows:
                # Extract TD cells and their classes
                td_matches = re.findall(r'<td[^>]*class=[\'\"]?([^\'\">]+)[\'\"]?[^>]*>(.*?)</td>', row_html, flags=re.S | re.I)
                if not td_matches:
                    continue
                # Normalize cells as list of (class, text)
                norm = []
                for cls, td in td_matches:
                    txt = re.sub(r'<[^>]+>', ' ', html.unescape(td)).strip()
                    txt = re.sub(r'\s+', ' ', txt)
                    norm.append((cls.lower(), txt))

                # Walk through sequence looking for repeating groups (elvl -> jpi -> t12)
                i = 0
                while i < len(norm):
                    cls, txt = norm[i]
                    if 'elvl' in cls:
                        # energy cell
                        energy_txt = txt
                        # find next jpi and next t12 cells within next 6 cells
                        jpi_txt = ''
                        t12_txt = ''
                        for k in range(i+1, min(i+7, len(norm))):
                            c_k, t_k = norm[k]
                            if 'jpi' in c_k and not jpi_txt:
                                jpi_txt = t_k
                            if 't12' in c_k and not t12_txt:
                                t12_txt = t_k
                            if jpi_txt and t12_txt:
                                break

                        # parse energy (handle '80  50' meaning 80(50) and prefer explicit pair)
                        e_val = None; e_unc = None
                        # explicit two-number uncertainty '80  50' (base then uncertainty)
                        m_pair = re.search(r'([0-9]+(?:\.[0-9]+)?)\s+([0-9]+(?:\.[0-9]+)?)', energy_txt)
                        if m_pair:
                            try:
                                e_val = float(m_pair.group(1))
                                e_unc = float(m_pair.group(2))
                            except Exception:
                                e_val = None
                        # prefer numeric at end of the energy text if pair not found
                        if e_val is None:
                            end_match = re.search(r'([0-9]+(?:\.[0-9]+)?)(?:\s*\(\s*\d+\s*\))?\s*$', energy_txt)
                            if end_match:
                                try:
                                    e_val, e_unc = parse_value_with_unc(end_match.group(0))
                                except Exception:
                                    e_val = None
                        if e_val is None:
                            m = re.search(r'([0-9]+(?:\.[0-9]+)?(?:\s*\(\s*\d+\s*\))?)', energy_txt)
                            if m:
                                try:
                                    e_val, e_unc = parse_value_with_unc(m.group(1))
                                except Exception:
                                    e_val = None

                        # combined text to detect decay channel
                        combined = ' '.join([energy_txt, jpi_txt, t12_txt]).lower()
                        decay_present = re.search(r'\b(β|beta|it|α|alpha|ec|electron capture|p\-|n\-|beta-delayed|proton|neutron)\b', combined, flags=re.I)
                        if not decay_present:
                            i += 1
                            continue

                        # require either an energy (possibly zero) or a half-life to include
                        if e_val is None and not t12_txt:
                            i += 1
                            continue

                        # parse half-life if present
                        hl_val_s = None; hl_unc_s = None
                        if t12_txt:
                            hl_match = re.search(r'([0-9]+(?:\.[0-9]+)?(?:\s*\(\s*\d+\s*\))?)(?:\s*(ps|ns|us|µs|ms|s|min|h|d|y|yr))', t12_txt, flags=re.I)
                            if hl_match:
                                base_hl, unc_hl = parse_value_with_unc(hl_match.group(1))
                                unit = hl_match.group(2).lower()
                                unit_map = {'ps':1e-12,'ns':1e-9,'us':1e-6,'µs':1e-6,'ms':1e-3,'s':1.0,'min':60.0,'h':3600.0,'d':86400.0,'y':31557600.0,'yr':31557600.0}
                                factor = unit_map.get(unit, 1.0)
                                if base_hl is not None:
                                    hl_val_s = base_hl * factor
                                if unc_hl is not None:
                                    hl_unc_s = unc_hl * factor

                        # extract jpi symbol (prefer parenthesized form)
                        jpi = ''
                        jm_all = re.findall(r'\(\s*([0-9]+(?:/?[0-9]+)?\s*[+\-])\s*\)', jpi_txt)
                        if jm_all:
                            jpi = jm_all[-1].strip()
                        else:
                            jm = re.search(r'([0-9]+(?:/?[0-9]+)?\s*[+\-])', jpi_txt)
                            if jm:
                                jpi = jm.group(1).strip()

                        # Build entry
                        entry: dict[str, str] = {}
                        if jpi:
                            entry['jpi'] = jpi
                        entry['e_keV'] = f"{e_val:.3f}"
                        if e_unc is not None:
                            entry['de_keV'] = f"{e_unc:.3f}"
                        if hl_val_s is not None:
                            entry['t12'] = f"{hl_val_s:.6g} s"
                        if hl_unc_s is not None:
                            entry['dt12'] = f"{hl_unc_s:.6g} s"
                        entry['source_row'] = ' | '.join([c + ':' + t for c,t in norm[max(0,i-1):min(len(norm), i+6)]])
                        state_entries.append(entry)
                        i += 1
                    else:
                        i += 1

            if state_entries:
                # Post-process: deduplicate by normalized Jπ (remove spaces), prefer entries
                # that have energy and half-life information.
                dedup: dict[str, dict] = {}
                def score(ent: dict) -> int:
                    s = 0
                    if ent.get('e_keV'): s += 2
                    if ent.get('t12'): s += 1
                    return s
                for ent in state_entries:
                    jraw = ent.get('jpi','') or ''
                    jnorm = re.sub(r"\s+", '', jraw)
                    if not jnorm:
                        # keep entries without jpi under special key (use numeric index)
                        jnorm = f'__nojp__{len(dedup)}'
                    if jnorm not in dedup or score(ent) > score(dedup[jnorm]):
                        dedup[jnorm] = ent
                result.mother_states = list(dedup.values())
                if len(state_entries) == 1:
                    result.mother_half_life_str = result.mother_states[0].get('t12', result.mother_half_life_str)
                    result.mother_spin_parity_str = result.mother_states[0].get('jpi', result.mother_spin_parity_str)
                else:
                    if not result.mother_half_life_str:
                        result.mother_half_life_str = '; '.join(e.get('t12','') for e in result.mother_states if e.get('t12')) or result.mother_half_life_str
                    if not result.mother_spin_parity_str:
                        result.mother_spin_parity_str = '; '.join(e.get('jpi','') for e in result.mother_states if e.get('jpi')) or result.mother_spin_parity_str
        except (KeyError, TypeError, ValueError, re.error) as e:
            logger.warning(f"Error parsing NNDC dataset HTML: {e}")

        return result

    def fetch_q_value(self, parent_nucleus: str, daughter_nucleus: str, decay_mode: str = "beta-") -> NNDCData:
        """Fetch Q-value for beta decay from NNDC NSR database.
        
        Args:
            parent_nucleus: Parent nucleus symbol (e.g., '122Ag')
            daughter_nucleus: Daughter nucleus symbol (e.g., '122Cd')
            decay_mode: Decay mode ('beta-', 'beta+', 'EC', etc.)
            
        Returns:
            NNDCData with Q-value information
            
        Raises:
            FetchError: If the API query fails
        """
        cache_key = self._cache_key(parent_nucleus, daughter_nucleus, decay_mode)
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached Q-value for {parent_nucleus}→{daughter_nucleus}")
            return cached
        
        result = NNDCData(source="NNDC NSR")
        
        try:
            # Use dataset page (getdataset.jsp) exclusively as requested
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
        """Fetch separation energies (Sn, Sp) for a nucleus.
        
        Args:
            nucleus: Nucleus symbol (e.g., '122Cd')
            
        Returns:
            NNDCData with separation energy information
            
        Raises:
            FetchError: If the API query fails
        """
        cache_key = f"sep_energy:{nucleus.lower()}"
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached separation energies for {nucleus}")
            return cached
        
        result = NNDCData(source="NNDC Nudat3")
        
        try:
            # Prefer dataset page only
            dataset_html = self._query_nudat3_dataset(nucleus)
            if dataset_html:
                # dataset parser also extracts Sn/Sp when present
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
        """Fetch nuclear properties (half-life, spin/parity) for a nucleus.
        
        Args:
            nucleus: Nucleus symbol (e.g., '122Ag')
            
        Returns:
            NNDCData with nuclear property information
            
        Raises:
            FetchError: If the API query fails
        """
        cache_key = f"properties:{nucleus.lower()}"
        cached = self._get_cached(cache_key)
        if cached is not None:
            logger.info(f"Using cached properties for {nucleus}")
            return cached
        
        result = NNDCData(source="NNDC Nudat3")
        
        try:
            # Use dataset page exclusively for nuclear properties
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
        """Convert nucleus symbol (e.g., '122Ag') to NNDC format.
        
        Args:
            nucleus: Nucleus symbol, e.g., "122Ag", "122ag", "Ag-122"
            
        Returns:
            NNDC nucleus ID, e.g., "122AG"
        """
        import re
        
        # Remove hyphens and normalize
        nucleus = nucleus.replace('-', '').strip()
        
        # Extract A and Z from formats like "122Ag" or "Ag122"
        match = re.match(r'^(\d+)([a-zA-Z]+)$', nucleus)
        if match:
            mass_num = match.group(1)
            symbol = match.group(2).upper()
            return f"{mass_num}{symbol}"
        
        match = re.match(r'^([a-zA-Z]+)(\d+)$', nucleus)
        if match:
            symbol = match.group(1).upper()
            mass_num = match.group(2)
            return f"{mass_num}{symbol}"
        
        # If no match, return uppercase as-is
        return nucleus.upper()
    
    def _query_nudat3(self, nucleus_id: str) -> Optional[Dict[str, Any]]:
        """Query NNDC Nudat3 database for a nucleus.
        
        Args:
            nucleus_id: Nucleus ID in NNDC format (e.g., "122AG")
            
        Returns:
            JSON response as dict, or None if not found
            
        Raises:
            FetchError: If the HTTP request fails
        """
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
                    if isinstance(gs, dict) and 'spin_parity' in gs:
                        result.mother_spin_parity_str = gs['spin_parity']
                # Additionally, extract level list if present to enumerate excited states
                if 'levels' in data and isinstance(data['levels'], list):
                    state_entries: list[dict[str, str]] = []
                    for lvl in data['levels']:
                        try:
                            jpi = lvl.get('spin_parity') or lvl.get('jpi')
                            hl = lvl.get('half_life') or lvl.get('t1/2')
                            energy = lvl.get('energy') or lvl.get('energy_keV') or lvl.get('e_keV')
                            de = lvl.get('energy_uncertainty') or lvl.get('dE') or lvl.get('de_keV')
                            entry: dict[str, str] = {}
                            if jpi:
                                entry['jpi'] = str(jpi)
                            if hl:
                                entry['t12'] = str(hl)
                            if energy is not None:
                                entry['e_keV'] = str(energy)
                            if de is not None:
                                entry['de_keV'] = str(de)
                            if entry:
                                state_entries.append(entry)
                        except Exception:
                            continue
                    if state_entries:
                        result.mother_states = state_entries
                        # Compose compact strings for display if single or multiple
                        if len(state_entries) == 1:
                            result.mother_half_life_str = state_entries[0].get('t12', result.mother_half_life_str)
                            result.mother_spin_parity_str = state_entries[0].get('jpi', result.mother_spin_parity_str)
                        else:
                            if not result.mother_half_life_str:
                                result.mother_half_life_str = '; '.join(e.get('t12','') for e in state_entries if e.get('t12')) or result.mother_half_life_str
                            if not result.mother_spin_parity_str:
                                result.mother_spin_parity_str = '; '.join(e.get('jpi','') for e in state_entries if e.get('jpi')) or result.mother_spin_parity_str
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
                        if 'sp' in sep_data:
                            result.sp_keV = float(sep_data['sp'])
                            result.sp_str = f"{sep_data['sp']:.1f} keV"
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
