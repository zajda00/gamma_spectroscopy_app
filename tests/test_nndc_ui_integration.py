"""Integration tests for NNDC UI widgets in main window."""

import pytest
from unittest.mock import Mock, patch, MagicMock
from PySide6.QtWidgets import QApplication, QLineEdit
from PySide6.QtCore import Qt

from app_nndc.nndc_client import NNDCData, NNDCClient
from app_nndc.ui_widgets import create_nndc_fetch_button
from app_decay_scheme.main_window import MainWindow


@pytest.fixture
def qapp():
    """Provide QApplication for UI tests."""
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_nndc_fetch_button_creation(qapp):
    """Test that NNDC fetch button can be created."""
    # Mock callbacks
    parent_getter = Mock(return_value="122Ag")
    daughter_getter = Mock(return_value="122Cd")
    on_success = Mock()
    
    # Create button
    button = create_nndc_fetch_button(
        "Test Button",
        parent_getter,
        daughter_getter,
        'fetch_q_value',
        on_success
    )
    
    # Verify button properties
    assert button.text() == "Test Button"
    assert button.isEnabled()


def test_nndc_data_auto_fill():
    """Test that NNDC data can be used to fill form fields."""
    # Create form fields
    qbeta_field = QLineEdit()
    dqbeta_field = QLineEdit()
    
    # Create mock NNDC data
    data = NNDCData(
        q_beta_keV=5123.45,
        dq_beta_keV=42.10,
        source="NNDC"
    )
    
    # Simulate auto-fill
    if data.q_beta_keV is not None:
        qbeta_field.setText(f"{data.q_beta_keV:.2f}")
    if data.dq_beta_keV is not None:
        dqbeta_field.setText(f"{data.dq_beta_keV:.2f}")
    
    # Verify fields were filled
    assert qbeta_field.text() == "5123.45"
    assert dqbeta_field.text() == "42.10"


def test_nndc_mother_properties_auto_fill():
    """Test auto-filling mother nuclear properties."""
    # Create form fields
    spinpar_field = QLineEdit()
    t12_field = QLineEdit()
    q_field = QLineEdit()
    sn_field = QLineEdit()
    pn_field = QLineEdit()
    
    # Create mock NNDC data
    data = NNDCData(
        mother_spin_parity_str="(1-)",
        mother_half_life_str="0.72(10) s",
        mother_q_str="9510(40) keV",
        mother_sn_str="8456.32 keV",
        mother_pn_str="8234.10 keV",
        source="NNDC"
    )
    
    # Simulate auto-fill
    if data.mother_spin_parity_str:
        spinpar_field.setText(data.mother_spin_parity_str)
    if data.mother_half_life_str:
        t12_field.setText(data.mother_half_life_str)
    if data.mother_q_str:
        q_field.setText(data.mother_q_str)
    if data.mother_sn_str:
        sn_field.setText(data.mother_sn_str)
    if data.mother_pn_str:
        pn_field.setText(data.mother_pn_str)
    
    # Verify all fields were filled
    assert spinpar_field.text() == "(1-)"
    assert t12_field.text() == "0.72(10) s"
    assert q_field.text() == "9510(40) keV"
    assert sn_field.text() == "8456.32 keV"
    assert pn_field.text() == "8234.10 keV"


def test_nndc_separation_energy_auto_fill():
    """Test auto-filling separation energy for neutrons."""
    sep_field = QLineEdit()
    type_combo = Mock()
    type_combo.currentText.return_value = 'n'
    
    # Create mock NNDC data
    data = NNDCData(
        sn_keV=8456.32,
        sp_keV=6234.10,
        source="NNDC"
    )
    
    # Simulate auto-fill based on type
    if type_combo.currentText() == 'n':
        if data.sn_keV is not None:
            sep_field.setText(f"{data.sn_keV:.1f}")
    
    assert sep_field.text() == "8456.3"


def test_nndc_error_handling():
    """Test that fetch errors are handled gracefully."""
    data = NNDCData(
        error_message="Network timeout",
        source="NNDC"
    )
    
    # Verify error state
    assert data.error_message is not None
    assert data.q_beta_keV is None
    assert data.mother_half_life_str is None


def test_nndc_partial_data():
    """Test handling of partial NNDC response."""
    # Only q_beta_keV is available, others are missing
    data = NNDCData(
        q_beta_keV=5000.0,
        mother_half_life_str=None,
        mother_spin_parity_str=None,
        source="NNDC"
    )
    
    qbeta_field = QLineEdit()
    t12_field = QLineEdit()
    
    # Fill what's available
    if data.q_beta_keV is not None:
        qbeta_field.setText(f"{data.q_beta_keV:.2f}")
    if data.mother_half_life_str:
        t12_field.setText(data.mother_half_life_str)
    
    # Verify only q_beta was filled
    assert qbeta_field.text() == "5000.00"
    assert t12_field.text() == ""  # Not filled


def test_nndc_sep_energy_callback_uses_dsn_dsp_fields(qapp):
    """Ensure separation-energy callback fills Sn/Sp values and uncertainties from dsn/dsp."""
    dummy = MainWindow.__new__(MainWindow)
    dummy.sep_energy_type_combo = Mock()
    dummy.sep_energy_type_combo.currentText.return_value = 'n'
    dummy.sn_edit = QLineEdit()
    dummy.serr_edit = QLineEdit()
    dummy.on_input_changed = Mock()

    data = NNDCData(sn_keV=4770.0, dsn_keV=40.0, sp_keV=12210.0, dsp_keV=40.0)
    MainWindow._on_nndc_sep_energy_fetched(dummy, data)

    assert dummy.sn_edit.text() == '4770.0'
    assert dummy.serr_edit.text() == '40.0'

    dummy.sep_energy_type_combo.currentText.return_value = 'p'
    MainWindow._on_nndc_sep_energy_fetched(dummy, data)
    assert dummy.sn_edit.text() == '12210.0'
    assert dummy.serr_edit.text() == '40.0'
    assert dummy.on_input_changed.call_count == 2


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
