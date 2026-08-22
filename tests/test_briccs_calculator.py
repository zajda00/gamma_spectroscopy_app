"""Tests for app_bricc ICC calculator with mocked BrIccS output.

These tests do not require the actual BrIccS executable to be installed.
They use mocked XML responses to validate the calculator logic, validation,
and caching without external dependencies.
"""

import unittest
from unittest.mock import patch, MagicMock
from app_bricc.calculator import (
    BrIccResult,
    calculate_icc,
    validate_parameters,
    get_cache_key,
    parse_briccs_xml,
    build_briccs_command,
    execute_briccs_command,
    clear_cache,
    ICC_CACHE,
    VALID_MULTIPOLARITIES,
    VALID_SHELLS,
)


# ============================================================================
# Mocked XML Responses
# ============================================================================

MOCKED_XML_AG110 = """<?xml version="1.0" encoding="utf-8"?>
<BrIcc>
  <Transition Z="47" E="100.0" M="M1">
    <AlphaCoeff Shell="K" Value="0.00523"/>
    <AlphaCoeff Shell="L" Value="0.00085"/>
    <AlphaCoeff Shell="M" Value="0.00012"/>
    <AlphaTotal Value="0.00620"/>
  </Transition>
</BrIcc>
"""

MOCKED_XML_SN122 = """<?xml version="1.0" encoding="utf-8"?>
<BrIcc>
  <Transition Z="50" E="564.0" M="E1">
    <AlphaCoeff Shell="K" Value="0.00145"/>
    <AlphaCoeff Shell="L" Value="0.00028"/>
    <AlphaTotal Value="0.00173"/>
  </Transition>
</BrIcc>
"""

MOCKED_XML_EMPTY = """<?xml version="1.0" encoding="utf-8"?>
<BrIcc/>
"""

MOCKED_XML_MALFORMED = """<?xml version="1.0" encoding="utf-8"?>
<BrIcc>
  <Transition Z="47" E="100.0"
"""  # Intentionally malformed


# ============================================================================
# Tests
# ============================================================================

class TestValidateParameters(unittest.TestCase):
    """Test parameter validation."""
    
    def test_valid_params_minimal(self):
        """Test validation with minimal required parameters."""
        is_valid, errors = validate_parameters(47, 100.0, "M1")
        self.assertTrue(is_valid)
        self.assertEqual(len(errors), 0)
    
    def test_valid_params_full(self):
        """Test validation with all parameters."""
        is_valid, errors = validate_parameters(
            Z=50, energy_keV=564.0, multipole="E1",
            delta=0.5, shell="K", dataset="BrIccFO"
        )
        self.assertTrue(is_valid)
        self.assertEqual(len(errors), 0)
    
    def test_invalid_Z_too_low(self):
        """Test Z below valid range."""
        is_valid, errors = validate_parameters(2, 100.0, "M1")
        self.assertFalse(is_valid)
        self.assertTrue(any("Z must be between" in e for e in errors))
    
    def test_invalid_Z_too_high(self):
        """Test Z above valid range."""
        is_valid, errors = validate_parameters(118, 100.0, "M1")
        self.assertFalse(is_valid)
        self.assertTrue(any("Z must be between" in e for e in errors))
    
    def test_invalid_Z_type(self):
        """Test Z with non-integer type."""
        is_valid, errors = validate_parameters("47", 100.0, "M1")
        self.assertFalse(is_valid)
        self.assertTrue(any("Z must be integer" in e for e in errors))
    
    def test_invalid_energy_negative(self):
        """Test negative energy."""
        is_valid, errors = validate_parameters(47, -100.0, "M1")
        self.assertFalse(is_valid)
        self.assertTrue(any("energy_keV must be positive" in e for e in errors))
    
    def test_invalid_energy_zero(self):
        """Test zero energy."""
        is_valid, errors = validate_parameters(47, 0.0, "M1")
        self.assertFalse(is_valid)
        self.assertTrue(any("energy_keV must be positive" in e for e in errors))
    
    def test_invalid_multipole(self):
        """Test invalid multipole."""
        is_valid, errors = validate_parameters(47, 100.0, "X1")
        self.assertFalse(is_valid)
        self.assertTrue(any("multipole" in e.lower() for e in errors))
    
    def test_invalid_shell(self):
        """Test invalid shell."""
        is_valid, errors = validate_parameters(47, 100.0, "M1", shell="X")
        self.assertFalse(is_valid)
        self.assertTrue(any("shell" in e.lower() for e in errors))
    
    def test_invalid_dataset(self):
        """Test invalid dataset."""
        is_valid, errors = validate_parameters(47, 100.0, "M1", dataset="BadDataset")
        self.assertFalse(is_valid)
        self.assertTrue(any("dataset" in e.lower() for e in errors))
    
    def test_valid_multipolarities(self):
        """Test that all valid multipolarities pass validation."""
        for mult in VALID_MULTIPOLARITIES:
            is_valid, errors = validate_parameters(47, 100.0, mult)
            self.assertTrue(is_valid, f"Multipole {mult} should be valid")
    
    def test_valid_shells(self):
        """Test that all valid shells pass validation."""
        for shell in VALID_SHELLS:
            is_valid, errors = validate_parameters(47, 100.0, "M1", shell=shell)
            self.assertTrue(is_valid, f"Shell {shell} should be valid")


class TestCacheKey(unittest.TestCase):
    """Test cache key generation."""
    
    def test_same_params_same_key(self):
        """Test that identical parameters produce identical keys."""
        key1 = get_cache_key(47, 100.0, "M1", delta=0.5, shell="K")
        key2 = get_cache_key(47, 100.0, "M1", delta=0.5, shell="K")
        self.assertEqual(key1, key2)
    
    def test_different_Z_different_key(self):
        """Test that different Z produces different key."""
        key1 = get_cache_key(47, 100.0, "M1")
        key2 = get_cache_key(48, 100.0, "M1")
        self.assertNotEqual(key1, key2)
    
    def test_different_energy_different_key(self):
        """Test that different energy produces different key."""
        key1 = get_cache_key(47, 100.0, "M1")
        key2 = get_cache_key(47, 101.0, "M1")
        self.assertNotEqual(key1, key2)
    
    def test_different_multipole_different_key(self):
        """Test that different multipole produces different key."""
        key1 = get_cache_key(47, 100.0, "M1")
        key2 = get_cache_key(47, 100.0, "E1")
        self.assertNotEqual(key1, key2)
    
    def test_key_is_deterministic(self):
        """Test that cache key is deterministic (reproducible)."""
        keys = [get_cache_key(47, 100.0, "M1") for _ in range(5)]
        self.assertEqual(len(set(keys)), 1, "All keys should be identical")


class TestParseXML(unittest.TestCase):
    """Test XML parsing."""
    
    def test_parse_valid_xml_ag110(self):
        """Test parsing of valid Ag-110 data."""
        data, warnings = parse_briccs_xml(MOCKED_XML_AG110)
        self.assertAlmostEqual(data["alpha_total"], 0.00620, places=5)
        self.assertAlmostEqual(data["alpha_K"], 0.00523, places=5)
        self.assertEqual(len(data["alpha_dict"]), 3)  # K, L, M
    
    def test_parse_valid_xml_sn122(self):
        """Test parsing of valid Sn-122 data."""
        data, warnings = parse_briccs_xml(MOCKED_XML_SN122)
        self.assertAlmostEqual(data["alpha_total"], 0.00173, places=5)
        self.assertAlmostEqual(data["alpha_K"], 0.00145, places=5)
    
    def test_parse_empty_xml(self):
        """Test parsing of empty XML."""
        data, warnings = parse_briccs_xml(MOCKED_XML_EMPTY)
        self.assertIsNone(data["alpha_total"])
        self.assertTrue(any("No alpha" in w for w in warnings))
    
    def test_parse_empty_string(self):
        """Test parsing of empty string."""
        data, warnings = parse_briccs_xml("")
        self.assertIsNone(data["alpha_total"])
        self.assertTrue(any("Empty" in w for w in warnings))
    
    def test_parse_malformed_xml(self):
        """Test parsing of malformed XML."""
        data, warnings = parse_briccs_xml(MOCKED_XML_MALFORMED)
        self.assertIsNone(data["alpha_total"])
        self.assertTrue(any("XML parse error" in w for w in warnings))


class TestBuildCommand(unittest.TestCase):
    """Test BrIccS command building."""
    
    def test_minimal_command(self):
        """Test command with minimal arguments."""
        cmd = build_briccs_command(47, 100.0, "M1")
        # Should contain: briccs, Z, energy, multipole, -X
        self.assertIn("briccs", cmd[0])
        self.assertIn("47", cmd)
        self.assertIn("100.0", cmd)
        self.assertIn("M1", cmd)
        self.assertIn("-X", cmd)
    
    def test_command_with_shell(self):
        """Test command with shell parameter."""
        cmd = build_briccs_command(47, 100.0, "M1", shell="K")
        self.assertIn("K", cmd)
    
    def test_command_with_delta(self):
        """Test command with mixing ratio."""
        cmd = build_briccs_command(47, 100.0, "M1", delta=0.5)
        self.assertIn("0.5", cmd)
    
    def test_command_with_dataset(self):
        """Test command with custom dataset."""
        cmd = build_briccs_command(47, 100.0, "M1", dataset="BrIccE")
        self.assertIn("-datafile", cmd)
        self.assertIn("BrIccE", cmd)


class TestCalculateICC(unittest.TestCase):
    """Test main ICC calculation with mocked subprocess."""
    
    def setUp(self):
        """Clear cache before each test."""
        clear_cache()
    
    def tearDown(self):
        """Clear cache after each test."""
        clear_cache()
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_calculate_valid_icc(self, mock_exec):
        """Test successful ICC calculation."""
        mock_exec.return_value = (MOCKED_XML_AG110, None)
        
        result = calculate_icc(47, 100.0, "M1")
        
        self.assertIsNone(result.error)
        self.assertAlmostEqual(result.alpha_total, 0.00620, places=5)
        self.assertAlmostEqual(result.alpha_K, 0.00523, places=5)
        self.assertEqual(result.Z, 47)
        self.assertEqual(result.energy_keV, 100.0)
        self.assertEqual(result.multipole, "M1")
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_calculate_with_all_parameters(self, mock_exec):
        """Test calculation with all optional parameters."""
        mock_exec.return_value = (MOCKED_XML_SN122, None)
        
        result = calculate_icc(
            Z=50, energy_keV=564.0, multipole="E1",
            delta=0.3, shell="K", dataset="BrIccE"
        )
        
        self.assertIsNone(result.error)
        self.assertEqual(result.delta, 0.3)
        self.assertEqual(result.dataset, "BrIccE")
    
    def test_calculate_invalid_parameters(self):
        """Test that invalid parameters return error without calling subprocess."""
        result = calculate_icc(2, 100.0, "M1")  # Z too low
        
        self.assertIsNotNone(result.error)
        self.assertIn("Z must be between", result.error)
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_calculate_subprocess_error(self, mock_exec):
        """Test handling of execution error."""
        mock_exec.return_value = ("", "BrIccS timeout")
        
        result = calculate_icc(47, 100.0, "M1")
        
        self.assertIsNotNone(result.error)
        self.assertIn("timeout", result.error.lower())
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_cache_hit(self, mock_exec):
        """Test that cached results are reused."""
        mock_exec.return_value = (MOCKED_XML_AG110, None)
        
        result1 = calculate_icc(47, 100.0, "M1", use_cache=True)
        
        # Reset mock call count
        mock_exec.reset_mock()
        
        result2 = calculate_icc(47, 100.0, "M1", use_cache=True)
        
        # Should not call subprocess second time
        mock_exec.assert_not_called()
        
        # Results should be identical
        self.assertEqual(result1.alpha_total, result2.alpha_total)
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_cache_bypass(self, mock_exec):
        """Test that cache can be bypassed."""
        mock_exec.return_value = (MOCKED_XML_AG110, None)
        
        result1 = calculate_icc(47, 100.0, "M1", use_cache=True)
        mock_exec.reset_mock()
        
        result2 = calculate_icc(47, 100.0, "M1", use_cache=False)
        
        # Should call subprocess even with cached result
        mock_exec.assert_called_once()
    
    @patch('app_bricc.calculator.execute_briccs_command')
    def test_different_params_different_results(self, mock_exec):
        """Test that different parameters produce different cache entries."""
        mock_exec.side_effect = [
            (MOCKED_XML_AG110, None),  # First call
            (MOCKED_XML_SN122, None),  # Second call
        ]
        
        result1 = calculate_icc(47, 100.0, "M1")
        result2 = calculate_icc(50, 564.0, "E1")
        
        self.assertNotEqual(result1.alpha_total, result2.alpha_total)
        self.assertEqual(mock_exec.call_count, 2)


class TestBrIccResult(unittest.TestCase):
    """Test BrIccResult dataclass."""
    
    def test_result_with_all_fields(self):
        """Test creating result with all fields populated."""
        result = BrIccResult(
            Z=47,
            energy_keV=100.0,
            multipole="M1",
            alpha_total=0.00620,
            alpha_K=0.00523,
            alpha_L=0.00085,
            alpha_M=0.00012,
            delta=0.5,
            dataset="BrIccFO",
            warnings=["test warning"],
            xml_output=MOCKED_XML_AG110,
        )
        
        self.assertEqual(result.Z, 47)
        self.assertEqual(result.energy_keV, 100.0)
        self.assertAlmostEqual(result.alpha_total, 0.00620, places=5)
        self.assertEqual(len(result.warnings), 1)
    
    def test_result_error_message(self):
        """Test result with error message."""
        result = BrIccResult(
            Z=47,
            energy_keV=100.0,
            multipole="M1",
            error="Parameter validation failed",
        )
        
        self.assertIsNotNone(result.error)
        self.assertIsNone(result.alpha_total)


if __name__ == "__main__":
    unittest.main()
