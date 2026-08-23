"""Tests for NNDC client module."""

import pytest
from app_nndc.nndc_client import NNDCClient, NNDCData, FetchError


def test_nucleus_id_normalization():
    """Test normalization of nucleus IDs."""
    client = NNDCClient()
    
    # Test various input formats
    assert client._normalize_nucleus_id('122Ag') == '122AG'
    assert client._normalize_nucleus_id('122ag') == '122AG'
    assert client._normalize_nucleus_id('Ag122') == '122AG'
    assert client._normalize_nucleus_id('AG-122') == '122AG'
    assert client._normalize_nucleus_id('122CD') == '122CD'


def test_nndc_data_structure():
    """Test NNDCData dataclass."""
    data = NNDCData(
        q_beta_keV=5123.4,
        dq_beta_keV=42.1,
        mother_half_life_str="0.72(10) s",
        source="NNDC"
    )
    
    assert data.q_beta_keV == 5123.4
    assert data.dq_beta_keV == 42.1
    assert data.mother_half_life_str == "0.72(10) s"
    assert data.source == "NNDC"
    assert data.error_message is None


def test_cache_functionality():
    """Test NNDC cache behavior."""
    client = NNDCClient(use_cache=True)
    
    # Create test data
    data1 = NNDCData(q_beta_keV=1000.0, source="test")
    key = client._cache_key("122Ag", "122Cd", "beta-")
    
    # Set and retrieve from cache
    client._set_cached(key, data1)
    cached = client._get_cached(key)
    
    assert cached is not None
    assert cached.q_beta_keV == 1000.0
    
    # Clear cache
    client.clear_cache()
    cached = client._get_cached(key)
    assert cached is None


def test_cache_disabled():
    """Test that cache can be disabled."""
    client = NNDCClient(use_cache=False)
    
    data = NNDCData(q_beta_keV=500.0, source="test")
    key = client._cache_key("122Ag", "122Cd", "beta-")
    
    # Try to cache - should be skipped
    client._set_cached(key, data)
    cached = client._get_cached(key)
    
    assert cached is None


def test_parse_nudat3_response():
    """Test parsing of Nudat3 API response."""
    client = NNDCClient()
    
    # Mock response with Q-value data
    mock_response = {
        'decay_schemes': [
            {
                'q_value': 5123.45,
                'daughter': '122Cd'
            }
        ],
        'half_life': '0.72(10) s',
        'ground_state': {
            'spin_parity': '(1-)',
            'half_life': '0.72 s'
        }
    }
    
    data = NNDCData()
    result = client._parse_nudat3_response(mock_response, data)
    
    assert result.q_beta_keV == 5123.45
    assert result.mother_half_life_str == '0.72(10) s'
    assert result.mother_spin_parity_str == '(1-)'


def test_parse_separation_energies():
    """Test parsing of separation energy data."""
    client = NNDCClient()
    
    mock_response = {
        'separation_energy': {
            'sn': 8456.32,
            'sp': 6234.10
        }
    }
    
    data = NNDCData()
    result = client._parse_separation_energies(mock_response, data)
    
    assert result.sn_keV == 8456.32
    assert result.sp_keV == 6234.10
    assert result.sn_str == '8456.3 keV'
    assert result.sp_str == '6234.1 keV'


def test_parse_nuclear_properties():
    """Test parsing of nuclear properties."""
    client = NNDCClient()
    
    mock_response = {
        'ground_state': {
            'half_life': '1.23(5) s',
            'spin_parity': '(9-)'
        }
    }
    
    data = NNDCData()
    result = client._parse_nuclear_properties(mock_response, data)
    
    assert result.mother_half_life_str == '1.23(5) s'
    assert result.mother_spin_parity_str == '(9-)'


def test_error_handling_in_parsing():
    """Test that parsing handles malformed data gracefully."""
    client = NNDCClient()
    
    # Test with invalid data types
    bad_response = {
        'decay_schemes': 'not_a_list',  # Should be list
        'half_life': None,
    }
    
    data = NNDCData()
    result = client._parse_nudat3_response(bad_response, data)
    
    # Should not crash, just leave fields empty/None
    assert result.q_beta_keV is None


def test_parse_dataset_q_uncertainty_and_separation_energies():
    """Test dataset-page parsing for Q uncertainty and daughter separation energies."""
    client = NNDCClient()
    html = '''
    <html><body>
    <p>Q(β-)=9.51×10 3 keV 4</p>
    <p>S(n)=4.77×10 3 keV</p>
    <p>S(p)=1.221×10 4 keV</p>
    <p>0.520 s 14 (3+) and 0.529 s 13 (1-)</p>
    </body></html>
    '''
    data = NNDCData()
    result = client._parse_dataset_html(html, data)
    assert result.q_beta_keV == 9510.0
    assert result.dq_beta_keV == 40.0
    assert result.sn_keV == 4770.0
    assert result.sp_keV == 12210.0
    assert len(result.mother_states) >= 2
    assert any(state['jpi'] == '(3+)' for state in result.mother_states)


def test_parse_dataset_multiple_mother_states():
    """Test parsing of several isomeric mother states from NNDC HTML."""
    client = NNDCClient()
    html = '''
    <html><body>
    0.520 s 14 (3+) weighted average ...
    0.529 s 13 (1-) from beta decay ...
    0.55 s 5 (9-) other ...
    </body></html>
    '''
    data = NNDCData()
    result = client._parse_dataset_html(html, data)
    jpis = {state['jpi'] for state in result.mother_states}
    assert '(3+)' in jpis
    assert '(1-)' in jpis
    assert '(9-)' in jpis


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
