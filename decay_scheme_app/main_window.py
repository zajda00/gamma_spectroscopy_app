from __future__ import annotations
from dataclasses import asdict
from pathlib import Path
from datetime import datetime
from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QPixmap
from PySide6.QtWidgets import (
    QMainWindow, QWidget, QFileDialog, QMessageBox, QVBoxLayout, QHBoxLayout,
    QTabWidget, QLabel, QLineEdit, QPushButton, QTableWidget, QTableWidgetItem,
    QSplitter, QPlainTextEdit, QCheckBox, QComboBox, QFormLayout, QScrollArea
)

from .abf import compute_abf
from .beta_inputs import dump_beta_inputs_to_text
from .eps_template import EpsTemplateEngine
from .loaders import ProjectDataLoader
from .logft import compute_logft
from .preview import render_eps_to_png, find_ghostscript_executable

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('Decay scheme app')
        self.resize(1600, 980)
        self.project = None
        self.project_folder: Path | None = None
        self.template_path: Path | None = None
        self.output_eps_path: Path | None = None
        self.preview_pixmap = None
        self.preview_zoom = 1.0
        self.preview_fit_to_window = True

        self._building = False
        self._setup_ui()
        self._setup_actions()
        
        # Auto-load data folder and template at startup
        self._try_auto_load_project()
        self._try_auto_load_template()
        self._build_output_path_if_needed()

    def _try_auto_load_project(self):
        """Attempt to auto-load project from 'data' folder at startup."""
        data_folder = Path('data').resolve()
        if not data_folder.exists():
            return
        try:
            loader = ProjectDataLoader()
            self.project = loader.load_project(data_folder)
            self.project_folder = data_folder
            self._populate_ui_from_project()
            self.recompute_everything()
            print(f'Auto-loaded project from {data_folder}')
        except Exception as exc:
            print(f'Auto-load from {data_folder} failed: {exc}')

    def _try_auto_load_template(self):
        """Attempt to auto-load template from 'templates/scheme_template.eps'."""
        template_file = Path('templates/scheme_template.eps').resolve()
        if not template_file.exists():
            return
        try:
            self.template_path = template_file
            print(f'Auto-loaded template from {template_file}')
        except Exception as exc:
            print(f'Auto-load template failed: {exc}')

    def _build_output_path_if_needed(self):
        """Build output path as outputs/parent_daughter_timestamp.eps if not set."""
        if self.output_eps_path is not None:
            return
        if self.project is None:
            return
        self.output_eps_path = self._build_output_path()

    def _build_output_path(self) -> Path:
        """Build output path: outputs/parent_daughter_timestamp.eps"""
        if self.project is None:
            return Path('output.eps')
        
        # Create outputs folder if needed
        outputs_dir = Path('outputs').resolve()
        outputs_dir.mkdir(parents=True, exist_ok=True)
        
        parent = self.project.beta_inputs.parent_nucleus.strip()
        daughter = self.project.beta_inputs.daughter_nucleus.strip()
        timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
        filename = f'{parent}_{daughter}_{timestamp}.eps'
        
        return outputs_dir / filename


    def _setup_ui(self):
        central = QWidget(self)
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)

        top = QHBoxLayout()
        self.load_btn = QPushButton('Load project folder')
        self.template_btn = QPushButton('Select scheme template')
        self.output_btn = QPushButton('Select output EPS/TXT')
        self.reload_btn = QPushButton('Reload Scheme')
        self.compute_btn = QPushButton('Recompute ABF / log ft')
        self.save_btn = QPushButton('Save project files')
        self.auto_write = QCheckBox('Auto write EPS')
        self.auto_write.setChecked(True)
        for w in [self.load_btn, self.template_btn, self.output_btn, self.reload_btn, self.compute_btn, self.save_btn, self.auto_write]:
            top.addWidget(w)
        layout.addLayout(top)

        self.tabs = QTabWidget()
        layout.addWidget(self.tabs)

        self.settings_tab = QWidget(); self.tabs.addTab(self.settings_tab, 'Settings')
        self.levels_tab = QWidget(); self.tabs.addTab(self.levels_tab, 'Levels')
        self.transitions_tab = QWidget(); self.tabs.addTab(self.transitions_tab, 'Transitions')
        self.results_tab = QWidget(); self.tabs.addTab(self.results_tab, 'ABF / log ft')
        self.preview_tab = QWidget(); self.tabs.addTab(self.preview_tab, 'Scheme preview')
        self.notes_tab = QWidget(); self.tabs.addTab(self.notes_tab, 'Notes')

        self._build_settings_tab()
        self._build_levels_tab()
        self._build_transitions_tab()
        self._build_results_tab()
        self._build_preview_tab()
        self._build_notes_tab()

        self.load_btn.clicked.connect(self.load_project_folder)
        self.template_btn.clicked.connect(self.select_template)
        self.output_btn.clicked.connect(self.select_output)
        self.reload_btn.clicked.connect(self.reload_scheme)
        self.compute_btn.clicked.connect(self.recompute_everything)
        self.save_btn.clicked.connect(self.save_project_files)
        self.auto_write.toggled.connect(lambda _: self.maybe_write_scheme())

    def _setup_actions(self):
        pass

    def _build_settings_tab(self):
        layout = QFormLayout(self.settings_tab)
        self.parent_edit = QLineEdit()
        self.daughter_edit = QLineEdit()
        self.qbeta_edit = QLineEdit()
        self.dqbeta_edit = QLineEdit()
        self.mother_spin_edit = QLineEdit()
        self.mother_t12_edit = QLineEdit()
        self.sn_edit = QLineEdit()
        self.sn_show_combo = QComboBox(); self.sn_show_combo.addItems(['0', '1'])
        self.abf_field_combo = QComboBox(); self.abf_field_combo.addItems(['absolute_percent', 'relative_percent'])
        layout.addRow('Parent nucleus', self.parent_edit)
        layout.addRow('Daughter nucleus', self.daughter_edit)
        layout.addRow('Qbeta [keV]', self.qbeta_edit)
        layout.addRow('dQbeta [keV]', self.dqbeta_edit)
        layout.addRow('Mother spin display', self.mother_spin_edit)
        layout.addRow('Mother half-life display', self.mother_t12_edit)
        layout.addRow('Neutron separation energy [keV]', self.sn_edit)
        layout.addRow('Show neutron separation', self.sn_show_combo)
        layout.addRow('ABF basis field', self.abf_field_combo)
        for w in [self.parent_edit, self.daughter_edit, self.qbeta_edit, self.dqbeta_edit, self.mother_spin_edit, self.mother_t12_edit, self.sn_edit]:
            w.editingFinished.connect(self._sync_settings_to_model)
        self.sn_show_combo.currentIndexChanged.connect(self._sync_settings_to_model)

    def _build_levels_tab(self):
        layout = QVBoxLayout(self.levels_tab)
        self.levels_table = QTableWidget()
        layout.addWidget(self.levels_table)
        self.levels_table.itemChanged.connect(self._levels_changed)

    def _build_transitions_tab(self):
        layout = QVBoxLayout(self.transitions_tab)
        self.transitions_table = QTableWidget()
        layout.addWidget(self.transitions_table)
        self.transitions_table.itemChanged.connect(self._transitions_changed)

    def _build_results_tab(self):
        layout = QVBoxLayout(self.results_tab)
        self.abf_no_table = QTableWidget(); self.abf_with_table = QTableWidget(); self.logft_table = QTableWidget()
        layout.addWidget(QLabel('ABF without ground-state feeding'))
        layout.addWidget(self.abf_no_table)
        layout.addWidget(QLabel('ABF with closure-based ground-state feeding'))
        layout.addWidget(self.abf_with_table)
        layout.addWidget(QLabel('Approximate local log ft'))
        layout.addWidget(self.logft_table)

    def _build_preview_tab(self):
        layout = QVBoxLayout(self.preview_tab)

        controls = QHBoxLayout()
        self.preview_fit_btn = QPushButton('Fit to window')
        self.preview_100_btn = QPushButton('100%')
        self.preview_zoom_in_btn = QPushButton('Zoom in')
        self.preview_zoom_out_btn = QPushButton('Zoom out')

        controls.addWidget(self.preview_fit_btn)
        controls.addWidget(self.preview_100_btn)
        controls.addWidget(self.preview_zoom_in_btn)
        controls.addWidget(self.preview_zoom_out_btn)
        controls.addStretch(1)
        layout.addLayout(controls)

        split = QSplitter(Qt.Horizontal)

        self.eps_preview = QPlainTextEdit()
        self.eps_preview.setReadOnly(True)

        self.preview_scroll = QScrollArea()
        self.preview_scroll.setWidgetResizable(False)

        self.png_label = QLabel('No rendered preview yet')
        self.png_label.setAlignment(Qt.AlignCenter)
        self.png_label.setScaledContents(False)
        self.png_label.resize(800, 1000)


        self.preview_scroll.setWidget(self.png_label)

        split.addWidget(self.eps_preview)
        split.addWidget(self.preview_scroll)
        split.setSizes([650, 950])

        layout.addWidget(split)

        self.preview_fit_btn.clicked.connect(self._preview_fit)
        self.preview_100_btn.clicked.connect(self._preview_100)
        self.preview_zoom_in_btn.clicked.connect(self._preview_zoom_in)
        self.preview_zoom_out_btn.clicked.connect(self._preview_zoom_out)

    def _preview_fit(self):
        self.preview_fit_to_window = True
        self._update_preview_pixmap()

    def _preview_100(self):
        self.preview_fit_to_window = False
        self.preview_zoom = 1.0
        self._update_preview_pixmap()

    def _preview_zoom_in(self):
        self.preview_fit_to_window = False
        self.preview_zoom *= 1.25
        self._update_preview_pixmap()

    def _preview_zoom_out(self):
        self.preview_fit_to_window = False
        self.preview_zoom /= 1.25
        self._update_preview_pixmap()


    def _update_preview_pixmap(self):
        if self.preview_pixmap is None:
            return

        if self.preview_fit_to_window:
            viewport_size = self.preview_scroll.viewport().size()
            scaled = self.preview_pixmap.scaled(
                viewport_size,
                Qt.KeepAspectRatio,
                Qt.SmoothTransformation,
            )
            self.png_label.setPixmap(scaled)
            self.png_label.resize(scaled.size())
        else:
            width = max(1, int(self.preview_pixmap.width() * self.preview_zoom))
            height = max(1, int(self.preview_pixmap.height() * self.preview_zoom))
            scaled = self.preview_pixmap.scaled(
                width,
                height,
                Qt.KeepAspectRatio,
                Qt.SmoothTransformation,
            )
            self.png_label.setPixmap(scaled)
            self.png_label.resize(scaled.size())



    def _build_notes_tab(self):
        layout = QVBoxLayout(self.notes_tab)
        self.notes_edit = QPlainTextEdit()
        layout.addWidget(self.notes_edit)

    def load_project_folder(self):
        folder = QFileDialog.getExistingDirectory(self, 'Select project folder')
        if not folder:
            return
        try:
            loader = ProjectDataLoader()
            self.project = loader.load_project(Path(folder))
            self.project_folder = Path(folder)
            self._populate_ui_from_project()
            self.recompute_everything()
        except Exception as exc:
            QMessageBox.critical(self, 'Load failed', str(exc))

    def select_template(self):
        path, _ = QFileDialog.getOpenFileName(self, 'Select scheme template txt/eps', '', 'Text or EPS (*.txt *.eps *.ps);;All files (*)')
        if path:
            self.template_path = Path(path)
            self.maybe_write_scheme(force=True)

    def select_output(self):
        path, _ = QFileDialog.getSaveFileName(self, 'Select output EPS/TXT', '', 'Text or EPS (*.txt *.eps *.ps);;All files (*)')
        if path:
            self.output_eps_path = Path(path)
            self.maybe_write_scheme(force=True)
        elif self.output_eps_path is None:
            # If user cancelled dialog and no output is set, generate auto path
            self.output_eps_path = self._build_output_path()

    def _populate_ui_from_project(self):
        self._building = True
        b = self.project.beta_inputs
        self.parent_edit.setText(b.parent_nucleus)
        self.daughter_edit.setText(b.daughter_nucleus)
        self.qbeta_edit.setText(str(b.qbeta_keV))
        self.dqbeta_edit.setText(str(b.dqbeta_keV))
        self.mother_spin_edit.setText(b.mother_spin_display)
        self.mother_t12_edit.setText(b.mother_half_life_display)
        self.sn_edit.setText('' if b.neutron_separation_energy_keV is None else str(b.neutron_separation_energy_keV))
        self.sn_show_combo.setCurrentText('1' if b.show_neutron_separation else '0')
        self.notes_edit.setPlainText(self.project.analysis_notes_text)
        self._fill_levels_table()
        self._fill_transitions_table()
        self._building = False

    def _fill_levels_table(self):
        cols = ['level_id', 'e_level_keV', 'jpi', 'jpi_origin_year', 'comments']
        self.levels_table.blockSignals(True)
        self.levels_table.setColumnCount(len(cols)); self.levels_table.setHorizontalHeaderLabels(cols)
        self.levels_table.setRowCount(len(self.project.levels))
        for i, lv in enumerate(self.project.levels):
            vals = [lv.level_id, f'{lv.e_level_keV:.2f}', lv.jpi, lv.jpi_origin_year, lv.comments]
            for j, v in enumerate(vals):
                self.levels_table.setItem(i, j, QTableWidgetItem(v))
        self.levels_table.blockSignals(False)

    def _fill_transitions_table(self):
        cols = ['transition_id', 'e_gamma_keV', 'level_initial_id', 'level_final_id', 'relative_percent', 'absolute_percent', 'origin', 'quality_flag', 'comment']
        self.transitions_table.blockSignals(True)
        self.transitions_table.setColumnCount(len(cols)); self.transitions_table.setHorizontalHeaderLabels(cols)
        self.transitions_table.setRowCount(len(self.project.transitions))
        for i, tr in enumerate(self.project.transitions):
            vals = [tr.transition_id, f'{tr.e_gamma_keV:.2f}', tr.level_initial_id, tr.level_final_id,
                    '' if tr.relative_percent is None else f'{tr.relative_percent:.4f}',
                    '' if tr.absolute_percent is None else f'{tr.absolute_percent:.4f}',
                    tr.origin, tr.quality_flag, tr.comment]
            for j, v in enumerate(vals):
                self.transitions_table.setItem(i, j, QTableWidgetItem(v))
        self.transitions_table.blockSignals(False)

    def _levels_changed(self, item):
        if self._building or self.project is None:
            return
        row = item.row(); col = item.column(); key = ['level_id','e_level_keV','jpi','jpi_origin_year','comments'][col]
        lv = self.project.levels[row]
        txt = item.text()
        if key == 'e_level_keV':
            try: lv.e_level_keV = float(txt)
            except ValueError: pass
        else:
            setattr(lv, key, txt)
        self.recompute_everything()

    def _transitions_changed(self, item):
        if self._building or self.project is None:
            return
        row = item.row(); col = item.column(); keys = ['transition_id','e_gamma_keV','level_initial_id','level_final_id','relative_percent','absolute_percent','origin','quality_flag','comment']
        key = keys[col]
        tr = self.project.transitions[row]
        txt = item.text()
        if key in {'e_gamma_keV','relative_percent','absolute_percent'}:
            try: setattr(tr, key, float(txt))
            except ValueError: pass
        else:
            setattr(tr, key, txt)
        self.recompute_everything()

    def _sync_settings_to_model(self):
        if self._building or self.project is None:
            return
        b = self.project.beta_inputs
        b.parent_nucleus = self.parent_edit.text().strip()
        b.daughter_nucleus = self.daughter_edit.text().strip()
        try: b.qbeta_keV = float(self.qbeta_edit.text())
        except ValueError: pass
        try: b.dqbeta_keV = float(self.dqbeta_edit.text())
        except ValueError: pass
        b.mother_spin_display = self.mother_spin_edit.text().strip()
        b.mother_half_life_display = self.mother_t12_edit.text().strip()
        try: b.neutron_separation_energy_keV = float(self.sn_edit.text()) if self.sn_edit.text().strip() else None
        except ValueError: pass
        b.show_neutron_separation = self.sn_show_combo.currentText() == '1'
        # Note: settings changes require manual 'Reload Scheme' button to regenerate EPS
        # This avoids unwanted regeneration during typing

    def recompute_everything(self):
        if self.project is None:
            return
        field = self.abf_field_combo.currentText()
        abf_no = compute_abf(self.project.levels, self.project.transitions, mode='no_ground_state', field=field)
        abf_with = compute_abf(self.project.levels, self.project.transitions, mode='with_ground_state_closure', field=field)
        self._fill_abf_table(self.abf_no_table, abf_no)
        self._fill_abf_table(self.abf_with_table, abf_with)
        logft = compute_logft(self.project.levels, abf_with, self.project.beta_inputs)
        self._fill_logft_table(logft)
        self.maybe_write_scheme(abf_with=abf_with, logft=logft)

    def reload_scheme(self):
        """Manually reload and regenerate scheme from current data."""
        if self.project is None:
            QMessageBox.warning(self, 'No project', 'Load a project first')
            return
        if self.template_path is None:
            QMessageBox.warning(self, 'No template', 'Select a template first')
            return
        # Ensure output path is set
        if self.output_eps_path is None:
            self.output_eps_path = self._build_output_path()
        # Recompute and regenerate scheme
        self.recompute_everything()
        # Force write, bypassing auto_write checkbox
        self.maybe_write_scheme(force=True)

    def _fill_abf_table(self, table, rows):
        cols = ['level_id','e_level_keV','jpi','incoming','outgoing','abf_raw','abf_clipped','mode']
        table.setColumnCount(len(cols)); table.setHorizontalHeaderLabels(cols); table.setRowCount(len(rows))
        for i, r in enumerate(rows):
            vals = [r.level_id, f'{r.e_level_keV:.2f}', r.jpi, f'{r.incoming:.4f}', f'{r.outgoing:.4f}', f'{r.abf_raw:.4f}', f'{r.abf_clipped:.4f}', r.mode]
            for j, v in enumerate(vals):
                table.setItem(i, j, QTableWidgetItem(v))

    def _fill_logft_table(self, rows):
        cols = ['level_id','e_level_keV','level_jpi','parent_state_id','parent_jpi','endpoint_keV','branch_percent','classification','logft']
        self.logft_table.setColumnCount(len(cols)); self.logft_table.setHorizontalHeaderLabels(cols); self.logft_table.setRowCount(len(rows))
        for i, r in enumerate(rows):
            vals = [r.level_id, f'{r.e_level_keV:.2f}', r.level_jpi, r.parent_state_id, r.parent_jpi, f'{r.endpoint_keV:.2f}', f'{r.branch_percent:.4f}', r.classification, '' if r.logft is None else f'{r.logft:.4f}']
            for j, v in enumerate(vals):
                self.logft_table.setItem(i, j, QTableWidgetItem(v))

    def maybe_write_scheme(self, force: bool = False, abf_with=None, logft=None):
        if self.project is None or self.template_path is None:
            return
        # Ensure output path is set, using auto-generated if needed
        if self.output_eps_path is None:
            self.output_eps_path = self._build_output_path()
        if not self.auto_write.isChecked() and not force:
            return
        try:
            engine = EpsTemplateEngine.from_file(self.template_path)
            if abf_with is None:
                abf_with = compute_abf(self.project.levels, self.project.transitions, mode='with_ground_state_closure', field=self.abf_field_combo.currentText())
            if logft is None:
                logft = compute_logft(self.project.levels, abf_with, self.project.beta_inputs)
            abf_map = {r.level_id: ('' if r.e_level_keV == 0 else f'{r.abf_clipped:.3f}') for r in abf_with}
            logft_map = {}
            for row in logft:
                label = '' if row.logft is None else f'{row.logft:.2f}'
                if row.level_id in logft_map and label:
                    logft_map[row.level_id] = f"{logft_map[row.level_id]}/{label}" if logft_map[row.level_id] else label
                elif label:
                    logft_map[row.level_id] = label
            text = engine.render(self.project.beta_inputs, self.project.levels, self.project.transitions, abf_map=abf_map, logft_map=logft_map)
            self.output_eps_path.write_text(text, encoding='utf-8')
            self.eps_preview.setPlainText(text)
            png_path = self.output_eps_path.with_suffix('.png')
            ok, preview_message = render_eps_to_png(self.output_eps_path, png_path)
            if ok:
                self.preview_pixmap = QPixmap(str(png_path))
                self._update_preview_pixmap()
            else:

                gs_path = find_ghostscript_executable() or 'not found'
                self.png_label.setText(
                    f'Preview not rendered. Output file updated\n{self.output_eps_path}\n\nGhostscript: {gs_path}\n\n{preview_message}'
                )
        except Exception as exc:
            self.eps_preview.setPlainText(f'Could not generate EPS preview: {exc}')
    def resizeEvent(self, event):
        super().resizeEvent(event)
        if getattr(self, 'preview_fit_to_window', False):
            self._update_preview_pixmap()

    def save_project_files(self):
        if self.project is None or self.project_folder is None:
            return
        import csv
        from .beta_inputs import dump_beta_inputs_to_text
        # save beta inputs
        (self.project_folder / 'beta_inputs_generated.yaml').write_text(dump_beta_inputs_to_text(self.project.beta_inputs), encoding='utf-8')
        # save levels
        with (self.project_folder / 'levels_edited.csv').open('w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=['level_id','nucleus','E_level_keV','dE_level_keV','Jpi','Jpi_origin_year','T12_s','dT12_s','comments'])
            w.writeheader()
            for lv in self.project.levels:
                w.writerow({'level_id':lv.level_id,'nucleus':lv.nucleus,'E_level_keV':lv.e_level_keV,'dE_level_keV':lv.de_level_keV or '','Jpi':lv.jpi,'Jpi_origin_year':lv.jpi_origin_year,'T12_s':lv.t12_s or '','dT12_s':lv.dt12_s or '','comments':lv.comments})
        with (self.project_folder / 'analysis_notes_edited.md').open('w', encoding='utf-8') as f:
            f.write(self.notes_edit.toPlainText())
        QMessageBox.information(self, 'Saved', f'Edited files written in {self.project_folder}')
