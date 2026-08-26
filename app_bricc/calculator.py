"""Internal Conversion Coefficient calculator using BrIccS subprocess.

This module wraps the BrIccS executable to calculate Internal Conversion
Coefficients (ICC) for nuclear transitions. It provides:
- Parameter validation (Z, energy, multipole)
- Result caching by parameter hash
- XML output parsing to structured dataclass
- Support for multiple datasets and shells
"""

from __future__ import annotations

import hashlib
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Optional

# ============================================================================
# Constants
# ============================================================================

# Valid atomic numbers (nuclear charge Z)
MIN_Z = 5
MAX_Z = 110

# Valid multipolarities for beta decay and transitions
VALID_MULTIPOLARITIES = {
    "E0", "M1", "E1", "M2", "E2", "M3", "E3",
    "M4", "E4", "M5", "E5", "M6", "E6"
}

# Valid nuclear shells for alpha coefficients
VALID_SHELLS = {"K", "L", "M", "N", "O", "P", "Q"}

# Valid BrIccS datasets
VALID_DATASETS = {
    "BrIccFO",     # Frozen Orbital (default)
    "BrIccE",      # Electronic
    "BrIccSE",     # Screening Estimate
}

DEFAULT_DATASET = "BrIccFO"
BRICCS_EXECUTABLE = Path(__file__).parent / "briccs"

# Global cache dictionary: key(Z, energy, multipole, delta, shell, dataset) -> BrIccResult
ICC_CACHE: Dict[str, BrIccResult] = {}


# ============================================================================
# Data Classes
# ============================================================================

@dataclass
class BrIccResult:
    """Result from BrIccS calculation.
    
    Attributes:
        Z: atomic number
        energy_keV: gamma ray energy in keV
        multipole: transition type (e.g. 'M1', 'E1')
        alpha_total: total ICC coefficient
        alpha_K: K-shell ICC coefficient
        alpha_L: L-shell ICC coefficient (if available)
        alpha_M: M-shell ICC coefficient (if available)
        alpha_dict: dict of all available alpha coefficients by shell
        delta: mixing ratio (if provided)
        dataset: BrIccS dataset used ('BrIccFO', 'BrIccE', 'BrIccSE')
        warnings: list of warnings from BrIccS
        xml_output: full XML response for diagnostics
    """
    Z: int
    energy_keV: float
    multipole: str
    alpha_total: Optional[float] = None
    alpha_K: Optional[float] = None
    alpha_L: Optional[float] = None
    alpha_M: Optional[float] = None
    alpha_dict: Dict[str, float] = field(default_factory=dict)
    delta: Optional[float] = None
    dataset: str = DEFAULT_DATASET
    warnings: list[str] = field(default_factory=list)
    xml_output: str = ""
    error: Optional[str] = None


# ============================================================================
# Validation Functions
# ============================================================================

def validate_parameters(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
) -> tuple[bool, list[str]]:
    """Validate ICC calculation parameters.
    
    Args:
        Z: atomic number (5-110)
        energy_keV: gamma ray energy in keV (must be positive)
        multipole: transition type from VALID_MULTIPOLARITIES
        delta: optional mixing ratio
        shell: optional (K, L, M, N, O, P, Q)
        dataset: optional dataset choice
    
    Returns:
        (is_valid: bool, errors: list[str]) where errors list contains validation messages
    """
    errors = []
    
    # Validate Z
    if not isinstance(Z, int):
        errors.append(f"Z must be integer, got {type(Z).__name__}")
    elif Z < MIN_Z or Z > MAX_Z:
        errors.append(f"Z must be between {MIN_Z} and {MAX_Z}, got {Z}")
    
    # Validate energy
    try:
        E = float(energy_keV)
        if E <= 0:
            errors.append(f"energy_keV must be positive, got {E}")
    except (TypeError, ValueError):
        errors.append(f"energy_keV must be numeric, got {type(energy_keV).__name__}")
    
    # Validate multipole
    if multipole not in VALID_MULTIPOLARITIES:
        errors.append(f"multipole '{multipole}' not in {sorted(VALID_MULTIPOLARITIES)}")
    
    # Validate delta if provided
    if delta is not None:
        try:
            float(delta)
        except (TypeError, ValueError):
            errors.append(f"delta must be numeric, got {type(delta).__name__}")
    
    # Validate shell if provided
    if shell is not None and shell not in VALID_SHELLS:
        errors.append(f"shell '{shell}' not in {sorted(VALID_SHELLS)}")
    
    # Validate dataset if provided
    if dataset is not None and dataset not in VALID_DATASETS:
        errors.append(f"dataset '{dataset}' not in {sorted(VALID_DATASETS)}")
    
    return len(errors) == 0, errors


# ============================================================================
# Caching
# ============================================================================

def get_cache_key(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
) -> str:
    """Generate cache key from ICC parameters.
    
    Returns:
        hexdigest of SHA256 hash of parameter tuple
    """
    key_parts = (Z, round(energy_keV, 4), multipole, delta, shell, dataset or DEFAULT_DATASET)
    key_str = str(key_parts)
    return hashlib.sha256(key_str.encode()).hexdigest()


# ============================================================================
# BrIccS Execution
# ============================================================================

def build_briccs_command(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
) -> list[str]:
    """Build BrIccS command line arguments.
    
    BrIccS standard syntax (based on IAEA documentation):
        briccs Z energy multipole [subshell] [delta] [options]
    
    We use XML output format for machine-readable results.
    
    Args:
        Z, energy_keV, multipole, delta, shell, dataset: ICC parameters
    
    Returns:
        Command line as list suitable for subprocess.Popen
    """
    if not BRICCS_EXECUTABLE.exists():
        raise FileNotFoundError(f"BrIccS executable not found at {BRICCS_EXECUTABLE}")
    
    cmd = [str(BRICCS_EXECUTABLE)]
    
    # Required positional arguments
    cmd.append(str(Z))
    cmd.append(str(energy_keV))
    cmd.append(multipole)
    
    # Optional: subshell (comes before delta in BrIccS)
    if shell:
        cmd.append(shell)
    
    # Optional: mixing ratio delta
    if delta is not None:
        cmd.append(str(delta))
    
    # Optional: dataset (if not default)
    if dataset and dataset != DEFAULT_DATASET:
        cmd.extend(["-datafile", dataset])
    
    # Request XML output (machine-readable)
    cmd.append("-X")
    
    return cmd


def execute_briccs_command(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
    timeout: int = 30,
) -> tuple[str, Optional[str]]:
    """Execute BrIccS subprocess and return XML output.
    
    Args:
        Z, energy_keV, multipole, delta, shell, dataset: ICC parameters
        timeout: subprocess timeout in seconds
    
    Returns:
        (xml_output: str, error: Optional[str])
        If execution succeeds, xml_output is non-empty and error is None.
        Otherwise xml_output is empty and error contains error message.
    """
    try:
        cmd = build_briccs_command(Z, energy_keV, multipole, delta, shell, dataset)
    except FileNotFoundError as e:
        return "", str(e)
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            env={"BrIccHome": str(BRICCS_EXECUTABLE.parent)},
        )
        
        if result.returncode != 0:
            error_msg = f"BrIccS returned {result.returncode}: {result.stderr}"
            return "", error_msg
        
        if not result.stdout:
            return "", "BrIccS produced no output"
        
        return result.stdout, None
    
    except subprocess.TimeoutExpired:
        return "", f"BrIccS timeout after {timeout}s"
    except Exception as e:
        return "", f"BrIccS execution failed: {e}"


# ============================================================================
# XML Parsing
# ============================================================================

def parse_briccs_xml(xml_str: str) -> tuple[Dict[str, Any], list[str]]:
    """Parse BrIccS XML output and extract ICC values.
    
    Expects XML format from BrIccS with elements like:
        <BrIcc>
          <Transition Z="..." E="..." M="...">
            <AlphaCoeff Shell="K" Value="..."/>
            <AlphaCoeff Shell="L" Value="..."/>
            <AlphaTotal Value="..."/>
          </Transition>
        </BrIcc>
    
    Args:
        xml_str: XML output from BrIccS
    
    Returns:
        (data_dict: dict, warnings: list[str])
        data_dict contains: alpha_total, alpha_K, alpha_L, alpha_M, alpha_dict
    """
    data = {
        "alpha_total": None,
        "alpha_K": None,
        "alpha_L": None,
        "alpha_M": None,
        "alpha_dict": {},
    }
    warnings = []
    
    if not xml_str.strip():
        warnings.append("Empty XML output")
        return data, warnings
    
    try:
        root = ET.fromstring(xml_str)
    except ET.ParseError as e:
        warnings.append(f"XML parse error: {e}")
        return data, warnings
    
    # Look for coefficient values in XML
    # Standard BrIccS XML structure varies, but typically includes AlphaCoeff elements
    alpha_dict = {}
    t_value = None
    
    # Try to extract from various possible XML structures
    for elem in root.iter():
        # Generic coefficient extraction
        if "Alpha" in elem.tag or "alpha" in elem.tag:
            shell = elem.get("Shell") or elem.get("shell") or elem.get("L")
            value = elem.get("Value") or elem.get("value") or elem.text
            
            if shell and value:
                try:
                    alpha_dict[shell.strip()] = float(value)
                except ValueError:
                    warnings.append(f"Could not parse alpha value for shell {shell}")
        
        # Look for total alpha
        if "Total" in elem.tag or "total" in elem.tag:
            value = elem.get("Value") or elem.get("value") or elem.text
            if value:
                try:
                    t_value = float(value)
                except ValueError:
                    pass
    
    data["alpha_dict"] = alpha_dict
    data["alpha_total"] = t_value or alpha_dict.get("Total")
    data["alpha_K"] = alpha_dict.get("K")
    data["alpha_L"] = alpha_dict.get("L")
    data["alpha_M"] = alpha_dict.get("M")
    
    if not alpha_dict:
        warnings.append("No alpha coefficients found in XML output")
    
    return data, warnings


# ============================================================================
# Main Calculator
# ============================================================================

def calculate_icc(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
    use_cache: bool = True,
    verbose: bool = False,
) -> BrIccResult:
    """Calculate Internal Conversion Coefficients using BrIccS.
    
    Args:
        Z: atomic number (5-110)
        energy_keV: gamma ray energy in keV (must be positive)
        multipole: transition type (E0, M1, E1, M2, E2, M3, E3, ...)
        delta: optional mixing ratio
        shell: optional specific shell (K, L, M, N, O, P, Q)
        dataset: optional dataset ('BrIccFO', 'BrIccE', 'BrIccSE'); default is BrIccFO
        use_cache: if True, return cached result if available
        verbose: if True, print debug information
    
    Returns:
        BrIccResult dataclass with coefficients and metadata
    
    Raises:
        ValueError: if parameters fail validation
    """
    # Set default dataset
    if dataset is None:
        dataset = DEFAULT_DATASET
    
    # Validate input parameters
    is_valid, errors = validate_parameters(Z, energy_keV, multipole, delta, shell, dataset)
    if not is_valid:
        return BrIccResult(
            Z=Z,
            energy_keV=energy_keV,
            multipole=multipole,
            delta=delta,
            dataset=dataset,
            error="; ".join(errors),
        )
    
    # Check cache
    cache_key = get_cache_key(Z, energy_keV, multipole, delta, shell, dataset)
    if use_cache and cache_key in ICC_CACHE:
        if verbose:
            print(f"[ICC Cache hit] {cache_key[:8]}...")
        return ICC_CACHE[cache_key]
    
    # Execute BrIccS
    xml_output, exec_error = execute_briccs_command(
        Z, energy_keV, multipole, delta, shell, dataset
    )
    
    # Create result object
    result = BrIccResult(
        Z=Z,
        energy_keV=energy_keV,
        multipole=multipole,
        delta=delta,
        dataset=dataset,
        xml_output=xml_output,
    )
    
    if exec_error:
        result.error = exec_error
        if verbose:
            print(f"[ICC Error] {exec_error}")
        return result
    
    # Parse XML output
    parsed_data, parse_warnings = parse_briccs_xml(xml_output)
    result.warnings.extend(parse_warnings)
    result.alpha_total = parsed_data.get("alpha_total")
    result.alpha_K = parsed_data.get("alpha_K")
    result.alpha_L = parsed_data.get("alpha_L")
    result.alpha_M = parsed_data.get("alpha_M")
    result.alpha_dict = parsed_data.get("alpha_dict", {})
    
    # Cache result
    ICC_CACHE[cache_key] = result
    
    if verbose:
        print(f"[ICC Calculated] Z={Z}, E={energy_keV} keV, M={multipole}")
        print(f"  alpha_total={result.alpha_total}")
        print(f"  shells={list(result.alpha_dict.keys())}")
    
    return result


def clear_cache():
    """Clear the ICC result cache."""
    ICC_CACHE.clear()
