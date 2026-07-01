# Workflow Refactoring - Summary

## ✅ Implementation Complete

### Modified Files
- **app_decay_scheme/main_window.py** — 756 lines (was 707)

### What Changed

#### 1. **No More Auto-Load**
App no longer automatically loads data from `data/` folder on startup.
- Old behavior: App scanned `data/` and tried to load data
- New behavior: User must explicitly click "Load input data" button

#### 2. **Manual Workflow: Input → Project Copy**
When user loads input data:
1. User clicks **"Load input data"** button
2. Selects a folder containing input files (e.g., `example_data/`)
3. Enters project name (e.g., `ag122_cd122_test`)
4. App automatically:
   - Creates folder: `data/ag122_cd122_test_2026-07-01_03-42-15/`
   - Copies all input files into it
   - Creates output folder: `outputs/ag122_cd122_test_2026-07-01_03-42-15/`
   - Loads project from the copied folder

**Why?** This workflow ensures original data is never modified, and each project is self-contained.

#### 3. **Template Auto-Load (Safe)**
- App still tries to load `templates/scheme_template.eps` automatically
- If missing, prints a diagnostic message (doesn't crash)
- User can still select different template manually via button

#### 4. **UI Changes**

**Top toolbar (was cluttered, now focused):**
- ❌ Removed: "Load project folder", "Select output EPS/TXT", "Reload Scheme"
- ✅ Kept: "Load input data", "Select scheme template", "Recompute ABF / log ft", "Save project files"

**Preview controls (right side of scheme preview):**
- ✅ Added: "Reload scheme" button — explicitly regenerate scheme from current GUI state
- ✅ Added: "Save scheme" button — explicitly save EPS/PNG to output folder
- ✅ Kept: Zoom buttons (Fit, 100%, Zoom in/out) and "Edit PostScript"

#### 5. **Reload Scheme Improvements**
Old behavior:
- Clicked "Reload" → unclear if it actually updated the GUI data
- Hard to know if parent/daughter changes were applied

New behavior:
- Clicks "Reload scheme" → Syncs GUI data to model  → Recomputes ABF/logft → Regenerates EPS/PNG → Shows confirmation message
- **Clear user feedback** that scheme was regenerated

#### 6. **Save Scheme (New)**
- New button to explicitly save EPS/PNG to output folder
- Shows user where files were saved
- Useful to confirm scheme is saved to the right location

#### 7. **Output Folder Management**
- Output EPS files go to: `outputs/<project_name>_<timestamp>/`
- Filename format: `<parent>_<daughter>_YYYY-MM-DD_HH-MM-SS.eps`
- Each "Reload" or "Save" creates a new timestamped EPS file (versioning)

#### 8. **Error Handling**
- If template not found: Shows diagnostic message, doesn't crash
- If Ghostscript fails: Shows clear error, EPS still saved, PNG skipped
- If no project loaded: "Reload" button shows warning dialog

---

## 🧪 How to Test

### Test 1: No Auto-Load on Startup
1. Close app completely
2. Start app: `python main.py`
3. **Expected:** Welcome dialog appears asking "Load input data now?"
4. **Expected:** No automatic project loading from `data/` folder

### Test 2: Manual Load → Project Creation
1. Click "Yes" in welcome dialog
2. Select: `example_data/` folder (or any input folder with CSV/MD files)
3. Enter project name: `test_ag122_2026` (any name you like)
4. **Expected:** Message shows folder created
5. **Verify:**
   ```bash
   ls data/
   # Should show: test_ag122_2026_2026-07-01_14-30-45/
   
   ls outputs/
   # Should show: test_ag122_2026_2026-07-01_14-30-45/
   ```

### Test 3: Data Copied
1. After load completes, check data folder:
   ```bash
   ls data/test_ag122_2026_2026-07-01_14-30-45/
   # Should show: beta_inputs_*.md, levels_*.csv, transitions_*.csv, analysis_notes.md, etc.
   ```
2. **Expected:** All files from `example_data/` are copied

### Test 4: Preview Shows on All Tabs
1. Navigate to "Settings" tab
2. **Expected:** Settings form on left, preview PNG on right
3. Navigate to "Levels" tab
4. **Expected:** Table on left, preview PNG on right (same image)
5. Repeat for "Transitions" and "ABF / log ft" tabs

### Test 5: Edit Settings → Reload Scheme
1. In Settings tab, change parent from `122Ag` to (any value)
2. Click **"Reload scheme"** button (right side of preview)
3. **Expected:** Dialog "Scheme reloaded - Scheme has been regenerated with current data."
4. Check preview updates with new scheme
5. Check `outputs/test_ag122_2026_2026-07-01_14-30-45/` folder:
   ```bash
   ls outputs/test_ag122_2026_2026-07-01_14-30-45/
   # Should now show multiple EPS files with different timestamps
   ```

### Test 6: Save Scheme
1. Click **"Save scheme"** button (right side of preview)
2. **Expected:** Dialog "Scheme saved in: outputs/..." shows files saved
3. **Verify:** EPS and PNG files exist in output folder

### Test 7: New Project (Session 2)
1. Close app
2. Start app again
3. **Expected:** Welcome dialog again (no auto-load), fresh state
4. Load input data again with new project name
5. **Expected:** New project folder created with new timestamp

### Test 8: Template Auto-Load
1. Verify `templates/scheme_template.eps` exists
2. Close app, start app
3. **Expected:** App loads template automatically (no manual selection needed)
4. Check console: `Auto-loaded template from /path/to/templates/scheme_template.eps`

### Test 9: Missing Template (Graceful)
1. Rename `templates/scheme_template.eps` to `scheme_template.eps.bak`
2. Close app, start app
3. **Expected:** App prints message but doesn't crash
4. Click "Load input data" to load project
5. Click "Reload scheme" → **Expected:** Warning "Select a template first"
6. Click "Select scheme template" button, pick the template manually
7. **Expected:** Reload now works
8. Rename template back: `mv scheme_template.eps.bak scheme_template.eps`

---

## 📊 Folder Structure After Testing

```
data/
  test_ag122_2026_2026-07-01_14-30-45/
    beta_inputs_122Ag_122Cd.md
    levels_122Cd.csv
    transitions_122Ag_122Cd.csv
    analysis_notes.md
    analysis_notes_edited.md (after edits + save)
    levels_edited.csv (after edits + save)

outputs/
  test_ag122_2026_2026-07-01_14-30-45/
    122Ag_122Cd_2026-07-01_14-30-45.eps (first save)
    122Ag_122Cd_2026-07-01_14-30-45.png (if GS OK)
    122Ag_122Cd_2026-07-01_14-31-10.eps (after reload)
    122Ag_122Cd_2026-07-01_14-31-10.png
```

---

## 🚀 New User Workflow (Summary)

### Session 1
```
App start
  ↓
Welcome → "Load input data?"
  ↓
Select input folder + project name
  ↓
App creates project folder + output folder
  ↓
Navigate tabs, edit data, preview updates on right
  ↓
"Reload scheme" (right side) → regenerate
  ↓
"Save scheme" (right side) → save to outputs/
  ↓
"Save project files" (top) → save edits to project folder
  ↓
Close app
```

### Session 2 (New Project)
```
App start (clean slate)
  ↓
Welcome → "Load input data?"
  ↓
(Repeat as Session 1 with different project name)
```

---

## ⚠️ Known Limitations / Future Work

1. **Multiple timestamped EPS files**
   - Each "Reload" creates a new EPS with new timestamp
   - This is intentional (versioning), but could add option to overwrite latest

2. **Template must be selected manually if not at default path**
   - App looks for `templates/scheme_template.eps` only
   - User can change path via button (current behavior preserved)

3. **No "Open existing project" button**
   - Currently only supports "Load input folder"  
   - Could add button to reopen previous project from `data/` folder

4. **No undo/redo**
   - Out of scope, users can manually edit project folder files if needed

---

## 🛠️ Technical Details for Developers

### Key Methods Added
- `_show_welcome_dialog()` — Welcome prompt
- `load_input_folder()` — Main entry point for manual load
- `_create_project_folder(project_name)` — Creates timestamped folder
- `_copy_input_files_to_project()` — Copies files
- `_create_output_folder()` — Sets up output destination
- `reload_scheme()` — Sync GUI → Model → Regen EPS → Show message
- `save_scheme()` — Explicitly save EPS/PNG to output folder
- `_build_output_path()` — Dynamic EPS filename based on parent/daughter

### Key Data Members
- `self.input_folder` — Original input location (not used after load)
- `self.project_folder` — Folder in `data/` with copied files
- `self.output_folder` — Folder in `outputs/` for generated schemes

### Removed Features
- Auto-load from `data/` on startup
- "Select output EPS/TXT" button (now automatic)

### Preserved Features
- Auto-write on data changes (toggle via checkbox)
- Zoom controls on preview
- Edit PostScript dialog
- Ghostscript PNG rendering (graceful fallback if missing)
- ABF/logft computation

---

## ✨ What's Better Now

✅ **Safer** — Original data never modified  
✅ **Clearer** — Explicit "Load", "Reload", "Save" buttons  
✅ **More organized** — One project = one data folder + one output folder  
✅ **Better UX** — User knows exactly where things are saved  
✅ **Versioned output** — Can keep multiple scheme iterations  
✅ **Diagnostic messages** — Clear feedback on errors/actions  

---

**Questions?** Check console output for diagnostic messages. If something doesn't work, let me know the error message!
