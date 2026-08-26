"""NNDC data fetch UI components for main window.

Provides buttons and dialogs for fetching nuclear data from NNDC databases.
"""

import logging
from typing import Optional, Callable
from PySide6.QtWidgets import (
    QPushButton, QDialog, QVBoxLayout, QHBoxLayout, 
    QLabel, QProgressBar, QMessageBox
)
from PySide6.QtCore import Qt, QThread, Signal

from .nndc_client import NNDCClient, NNDCData, FetchError

logger = logging.getLogger(__name__)


class NNDCFetchWorker(QThread):
    """Worker thread for NNDC API calls (non-blocking GUI)."""
    
    finished = Signal()
    error = Signal(str)
    result_ready = Signal(object)  # Emits NNDCData
    
    def __init__(self, client: NNDCClient, method_name: str, *args, **kwargs):
        super().__init__()
        self.client = client
        self.method_name = method_name
        self.args = args
        self.kwargs = kwargs
    
    def run(self):
        """Execute fetch in background thread."""
        try:
            method = getattr(self.client, self.method_name)
            result = method(*self.args, **self.kwargs)
            self.result_ready.emit(result)
            self.finished.emit()
        except Exception as e:
            self.error.emit(str(e))
            self.finished.emit()


class FetchProgressDialog(QDialog):
    """Modal dialog showing fetch progress."""
    
    def __init__(self, parent=None, title: str = "Fetching NNDC data..."):
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setModal(True)
        self.setWindowFlags(self.windowFlags() & ~Qt.WindowContextHelpButtonHint)
        
        layout = QVBoxLayout()
        self.label = QLabel("Fetching data from NNDC database...")
        self.progress = QProgressBar()
        self.progress.setMaximum(0)  # Indeterminate progress
        
        layout.addWidget(self.label)
        layout.addWidget(self.progress)
        
        self.setLayout(layout)
        self.setMinimumWidth(300)


def create_nndc_fetch_button(
    label: str,
    parent_nucleus_getter: Callable[[], str],
    daughter_nucleus_getter: Callable[[], str],
    fetch_method: str,
    on_success: Callable[[NNDCData], None],
    parent=None
) -> QPushButton:
    """Create a button that fetches NNDC data in a background thread.
    
    Args:
        label: Button text
        parent_nucleus_getter: Callback to get parent nucleus symbol
        daughter_nucleus_getter: Callback to get daughter nucleus symbol
        fetch_method: Name of NNDCClient method to call ('fetch_q_value', etc.)
        on_success: Callback(NNDCData) called on successful fetch
        parent: Parent widget
        
    Returns:
        QPushButton with integrated NNDC fetch functionality
    """
    button = QPushButton(label, parent)
    client = NNDCClient(use_cache=True)
    worker = None
    dialog = None
    
    def on_fetch_clicked():
        nonlocal worker, dialog
        
        parent_nuc = parent_nucleus_getter()
        daughter_nuc = daughter_nucleus_getter()
        
        if fetch_method == 'fetch_q_value':
            if not parent_nuc or not daughter_nuc:
                QMessageBox.warning(
                    parent,
                    "Missing data",
                    "Please specify both parent and daughter nuclei."
                )
                return
        elif fetch_method in {'fetch_nuclear_properties', 'fetch_separation_energies'}:
            if not parent_nuc:
                QMessageBox.warning(
                    parent,
                    "Missing data",
                    "Please specify the parent nucleus."
                )
                return
        
        # Show progress dialog
        dialog = FetchProgressDialog(
            parent,
            f"Fetching {fetch_method} from NNDC..."
        )
        
        # Create and start worker thread
        if fetch_method == 'fetch_q_value':
            worker = NNDCFetchWorker(
                client, fetch_method, parent_nuc, daughter_nuc, 'beta-'
            )
        elif fetch_method == 'fetch_nuclear_properties':
            worker = NNDCFetchWorker(client, fetch_method, parent_nuc)
        elif fetch_method == 'fetch_separation_energies':
            worker = NNDCFetchWorker(client, fetch_method, parent_nuc)
        else:
            QMessageBox.critical(parent, "Error", f"Unknown fetch method: {fetch_method}")
            return
        
        def on_result(data: NNDCData):
            dialog.close()
            if data.error_message:
                QMessageBox.warning(
                    parent,
                    "NNDC Fetch Error",
                    f"Failed to fetch data:\n{data.error_message}"
                )
            else:
                on_success(data)
                QMessageBox.information(
                    parent,
                    "Success",
                    "NNDC data fetched successfully."
                )
        
        def on_error(msg: str):
            dialog.close()
            QMessageBox.critical(
                parent,
                "NNDC Fetch Error",
                f"Error fetching data:\n{msg}"
            )
        
        worker.result_ready.connect(on_result)
        worker.error.connect(on_error)
        worker.finished.connect(dialog.close)
        
        dialog.show()
        worker.start()
    
    button.clicked.connect(on_fetch_clicked)
    return button
