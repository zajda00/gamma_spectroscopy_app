"""app_bricc package - Internal Conversion Coefficient calculator using BrIccS.

This module integrates the BrIccS executable to calculate ICC values without
implementing formulas from scratch. Results are cached and validated.
"""

from .calculator import (
    BrIccResult,
    calculate_icc,
    execute_briccs_command,
    validate_parameters,
    get_cache_key,
    ICC_CACHE,
)

__all__ = [
    "BrIccResult",
    "calculate_icc",
    "execute_briccs_command",
    "validate_parameters",
    "get_cache_key",
    "ICC_CACHE",
]
