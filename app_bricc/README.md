# app_bricc - Internal Conversion Coefficient Calculator

This module integrates the BrIccS executable for calculating Internal Conversion Coefficients (ICC) without implementing formulas from scratch.

## Features

- **Parameter Validation**: Z (5-110), positive energy, valid multipolarities
- **Result Caching**: Efficient caching by parameter hash to avoid redundant calculations
- **XML Parsing**: Automatically parses BrIccS XML output into structured dataclass
- **Error Handling**: Graceful handling of invalid parameters and execution errors
- **Multiple Datasets**: Support for BrIccFO (default), BrIccE, BrIccSE
- **Shell Selection**: Optionally request coefficients for specific shells (K, L, M, N, O, P, Q)
- **Test Coverage**: Comprehensive tests with mocked XML (no local installation required)

## Module Structure

```
app_bricc/
├── __init__.py          # Package exports
├── briccs               # BrIccS executable (ELF 64-bit binary)
├── calculator.py        # Main calculator module
└── README.md            # This file
```

## Usage

### Basic Example

```python
from app_bricc import calculate_icc

# Calculate ICC for Ag-110 M1 transition at 100 keV
result = calculate_icc(Z=47, energy_keV=100.0, multipole="M1")

print(f"alpha_total = {result.alpha_total}")
print(f"alpha_K = {result.alpha_K}")
print(f"Available shells: {list(result.alpha_dict.keys())}")
```

### Advanced Usage

```python
from app_bricc import calculate_icc

# Calculate with mixing ratio and specific shell, using different dataset
result = calculate_icc(
    Z=50,
    energy_keV=564.0,
    multipole="E1",
    delta=0.3,              # Mixing ratio (optional)
    shell="K",              # Specific shell (optional)
    dataset="BrIccE",       # Dataset choice (optional)
    use_cache=True,         # Cache result (default)
    verbose=True            # Print debug info (default False)
)

if result.error:
    print(f"Calculation failed: {result.error}")
else:
    print(f"Total ICC: {result.alpha_total}")
    print(f"Warnings: {result.warnings}")
    print(f"Dataset used: {result.dataset}")
```

## API Reference

### `BrIccResult` (dataclass)

Result object containing:
- **Z** (int): Atomic number
- **energy_keV** (float): Gamma ray energy
- **multipole** (str): Transition type (M1, E1, E2, etc.)
- **alpha_total** (float | None): Total ICC coefficient
- **alpha_K, alpha_L, alpha_M** (float | None): Shell-specific coefficients
- **alpha_dict** (dict): All available alpha values by shell
- **delta** (float | None): Mixing ratio (if provided)
- **dataset** (str): Which BrIccS dataset was used
- **warnings** (list[str]): Any warnings from calculation
- **xml_output** (str): Full XML response for diagnostics
- **error** (str | None): Error message if calculation failed

### `calculate_icc()`

```python
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
    
    Raises:
        ValueError: if parameters fail validation
    """
```

### `validate_parameters()`

```python
def validate_parameters(
    Z: int,
    energy_keV: float,
    multipole: str,
    delta: Optional[float] = None,
    shell: Optional[str] = None,
    dataset: Optional[str] = None,
) -> tuple[bool, list[str]]:
    """Check parameters before calculation.
    
    Returns:
        (is_valid: bool, errors: list[str])
    """
```

### Other Functions

- `get_cache_key()`: Generate deterministic cache key from parameters
- `execute_briccs_command()`: Run subprocess and return XML
- `build_briccs_command()`: Construct BrIccS command line
- `parse_briccs_xml()`: Parse XML output to dict
- `clear_cache()`: Clear the result cache

## Constants

- `MIN_Z, MAX_Z = 5, 110`: Valid atomic numbers
- `VALID_MULTIPOLARITIES`: E0, M1, E1, M2, E2, M3, E3, ...
- `VALID_SHELLS`: K, L, M, N, O, P, Q
- `VALID_DATASETS`: BrIccFO, BrIccE, BrIccSE
- `DEFAULT_DATASET = "BrIccFO"`

## Testing

Run all tests (no BrIccS installation needed):

```bash
python3 -m pytest tests/test_briccs_calculator.py -v
```

Tests use mocked XML responses and do not require:
- Local BrIccS executable
- BrIccHome environment variable
- External data files

## Implementation Details

### Parameter Validation

- **Z**: must be integer in [5, 110]
- **energy_keV**: must be positive float (typically < 3000 keV)
- **multipole**: must be in `VALID_MULTIPOLARITIES`
- **delta**: optional float (can be any value if provided)
- **shell**: if provided, must be in `VALID_SHELLS`
- **dataset**: if provided, must be in `VALID_DATASETS`

### Caching Strategy

Results are cached using SHA256 hash of parameter tuple: `(Z, energy_rounded, multipole, delta, shell, dataset)`.

Energy is rounded to 4 decimal places to handle floating-point precision.

### BrIccS Integration

The calculator builds a command line like:
```
./briccs Z energy multipole [shell] [delta] [-datafile dataset] -X
```

The `-X` flag requests XML output for machine-readable results.

Environment variable `BrIccHome` is set to the directory containing the executable.

### XML Parsing

Parses standard BrIccS XML output extracting:
- AlphaCoeff elements with Shell and Value attributes
- AlphaTotal element for total coefficient
- Warnings for missing or malformed data

## Future Extensions

The calculator is designed to be a standalone read-only module. Future extensions might include:

1. GUI integration for interactive calculation
2. Batch processing of multiple transitions
3. Integration with decay scheme data (extracting Z, energy, multipole from decay data)
4. Visualization of ICC results
5. Export to various formats (CSV, JSON, XML)

## Environment Requirements

- Python 3.8+
- No external dependencies for the calculator module itself
- For running BrIccS: Linux x86-64 system with required libraries
- For testing: `unittest` (stdlib)

## License and Citation

BrIccS is a product of the IAEA Nuclear Data Section.

Citation recommendation when using results from this module:
```
I.M. Band, M.B. Trzhaskovskaya, M.A. Listengarten
KKDEC - an electron conversion coefficient package
Nucl. Instrum. Methods A 348 (1994) 614-623
```
