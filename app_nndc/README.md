# NNDC Integration Module

Provides automated nuclear data fetching from the National Nuclear Data Center (NNDC) database.

## Overview

The `app_nndc` module integrates with the NNDC Nudat3 REST API to fetch:
- **Q-value data** for beta decay
- **Nuclear properties** (half-life, spin/parity)
- **Separation energies** (neutron/proton)

Data is fetched asynchronously (non-blocking GUI) with built-in caching to minimize API requests.

## Architecture

```
app_nndc/
  __init__.py          # Module exports
  nndc_client.py       # Core API client and data models
  ui_widgets.py        # PySide6 GUI components for data fetching
```

## Usage

### In Decay Scheme Application

Three fetch buttons are automatically added to the Settings panel:

1. **Q-value Section**: "Fetch from NNDC" button
   - Populates: `Qbeta [keV]`, `dQbeta [keV]`
   - Triggered by user clicking fetch button
   - Callback: `MainWindow._on_nndc_qvalue_fetched()`

2. **Mother Display Values Section**: "Fetch from NNDC" button
   - Populates: Spin/parity, T1/2, Q value, Sn, Pn
   - Queries parent nucleus properties
   - Callback: `MainWindow._on_nndc_mother_fetched()`

3. **Separation Energy Section**: "Fetch from NNDC" button
   - Populates: Value [keV] based on Type (n/p) selection
   - Callback: `MainWindow._on_nndc_sep_energy_fetched()`

### Programmatic Usage

```python
from app_nndc import NNDCClient

# Create client with caching enabled (default)
client = NNDCClient(use_cache=True)

# Fetch Q-value
q_data = client.fetch_q_value("122Ag", "122Cd", "beta-")
if q_data.q_beta_keV is not None:
    print(f"Q-value: {q_data.q_beta_keV} keV")
    print(f"Uncertainty: {q_data.dq_beta_keV} keV")

# Fetch nuclear properties
prop_data = client.fetch_nuclear_properties("122Ag")
if prop_data.mother_half_life_str:
    print(f"Half-life: {prop_data.mother_half_life_str}")

# Fetch separation energies
sep_data = client.fetch_separation_energies("122Cd")
if sep_data.sn_keV is not None:
    print(f"Sn: {sep_data.sn_keV:.2f} keV")
```

## API Reference

### NNDCClient

Main client for querying NNDC databases.

#### Constructor
```python
client = NNDCClient(use_cache: bool = True)
```

#### Methods

**fetch_q_value(parent_nucleus, daughter_nucleus, decay_mode="beta-") → NNDCData**
- Fetches Q-value for beta decay
- Parameters:
  - `parent_nucleus`: e.g., "122Ag"
  - `daughter_nucleus`: e.g., "122Cd"
  - `decay_mode`: default "beta-", can also be "beta+", "EC"
- Returns: `NNDCData` with `q_beta_keV` and `dq_beta_keV` fields

**fetch_nuclear_properties(nucleus) → NNDCData**
- Fetches T1/2, spin/parity, Q-value for a nucleus
- Parameters:
  - `nucleus`: nucleus symbol, e.g., "122Ag"
- Returns: `NNDCData` with mother property fields

**fetch_separation_energies(nucleus) → NNDCData**
- Fetches neutron/proton separation energy
- Parameters:
  - `nucleus`: nucleus symbol, e.g., "122Cd"
- Returns: `NNDCData` with `sn_keV` and `sp_keV` fields

**clear_cache()**
- Clears all cached data (useful after 24 hours or manual refresh)

### NNDCData

Dataclass containing fetched nuclear data.

#### Fields

**Q-value data**:
- `q_beta_keV: float | None` - Q-value in keV
- `dq_beta_keV: float | None` - Uncertainty in keV
- `q_beta_str: str | None` - Formatted Q-value string

**Mother/Parent nucleus data**:
- `mother_half_life_str: str | None` - Half-life with units (e.g., "0.72(10) s")
- `mother_spin_parity_str: str | None` - Spin/parity (e.g., "(1-)")
- `mother_q_str: str | None` - Q-value string (e.g., "9510(40) keV")
- `mother_sn_keV: float | None` - Sn in keV
- `mother_pn_keV: float | None` - Pn in keV

**Separation energies**:
- `sn_keV: float | None` - Neutron separation energy
- `sn_str: str | None` - Formatted Sn string
- `sp_keV: float | None` - Proton separation energy
- `sp_str: str | None` - Formatted Sp string

**Metadata**:
- `source: str` - Data source (e.g., "NNDC NSR", "NNDC Nudat3")
- `fetch_timestamp: datetime | None` - When data was fetched
- `error_message: str | None` - Error message if fetch failed

### FetchError

Exception raised when API query fails (network error, malformed response, etc.).

```python
try:
    data = client.fetch_q_value("122Ag", "122Cd")
except FetchError as e:
    print(f"Fetch failed: {e}")
```

## Caching Mechanism

- **Duration**: 24 hours from fetch time
- **Scope**: In-memory only (per-session)
- **Key format**: "parent→daughter:decay_mode" or "sep_energy:nucleus"
- **Automatic expiration**: Cached data older than 24 hours is discarded

To disable caching:
```python
client = NNDCClient(use_cache=False)
```

To manually clear cache:
```python
client.clear_cache()
```

## Nucleus ID Normalization

The client automatically normalizes nucleus identifiers:

| Input | Normalized |
|-------|-----------|
| "122Ag" | "122AG" |
| "122ag" | "122AG" |
| "Ag122" | "122AG" |
| "Ag-122" | "122AG" |
| "122-AG" | "122AG" |

## Error Handling

All fetch methods return `NNDCData` objects regardless of success/failure:
- On success: Data fields are populated, `error_message` is `None`
- On failure: Data fields are `None`, `error_message` contains description

This allows graceful degradation - the application continues with manual input if fetch fails.

```python
data = client.fetch_q_value("122Ag", "122Cd")

if data.error_message:
    # Fetch failed, use manual input or cached value
    print(f"Warning: {data.error_message}")
else:
    # Use fetched data
    auto_fill_q_value_field(data.q_beta_keV)
```

## UI Threading

All API calls execute in background threads (`QThread`) to prevent GUI freezing:

1. User clicks "Fetch from NNDC" button
2. Progress dialog opens ("Fetching data...")
3. API call runs in worker thread
4. On completion, progress dialog closes
5. Callback function called with result
6. Form fields auto-filled

## NNDC API Details

Currently uses the NNDC Nudat3 REST API:
- **Base URL**: `https://www.nndc.bnl.gov/nudat3/api/`
- **Endpoint**: `/nucleus/{ID}/` (e.g., `/nucleus/122AG/`)
- **Response format**: JSON
- **Rate limiting**: Not currently enforced (be respectful)
- **Authentication**: Not required

## Testing

```bash
# Run NNDC client unit tests
pytest tests/test_nndc_client.py -v

# Run UI integration tests
pytest tests/test_nndc_ui_integration.py -v

# Run all tests
pytest tests -q
```

## Performance Notes

- First fetch for a nucleus: ~1-2 seconds (API call)
- Cached fetch: <100ms (in-memory lookup)
- Non-blocking: GUI remains responsive during fetch
- Multiple concurrent fetches: Each runs in separate thread

## Limitations and Future Work

### Current Limitations
- Only supports NNDC Nudat3 API (not NSR decay data)
- In-memory cache only (lost on application restart)
- No offline mode
- Limited confidence/uncertainty representation

### Potential Enhancements
1. **Persistent cache**: Store to disk, survive restarts
2. **More data sources**: Add ENSDF, beta decay compilations
3. **Batch queries**: Fetch data for multiple nuclei at once
4. **Mock responses**: Store example responses for offline testing
5. **User preferences**: Remember successful fetches per project
6. **Advanced search**: Fuzzy matching for ambiguous nucleus names

## Related Files

- [app_decay_scheme/main_window.py](../app_decay_scheme/main_window.py) - GUI integration
- [tests/test_nndc_client.py](../tests/test_nndc_client.py) - Unit tests
- [tests/test_nndc_ui_integration.py](../tests/test_nndc_ui_integration.py) - Integration tests

## Author Notes

The module is designed for extensibility:
- New data types can be added to `NNDCData` dataclass
- Additional API endpoints can be wrapped with new `fetch_*` methods
- Custom parsing logic can override default `_parse_*` methods
- Caching strategy can be swapped by reimplementing cache methods
