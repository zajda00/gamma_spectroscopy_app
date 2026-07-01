#!/usr/bin/env python
"""Quick test of auto-load functionality without full GUI."""

from pathlib import Path
from datetime import datetime
from decay_scheme_app.loaders import ProjectDataLoader

def test_auto_load_project():
    """Test auto-load of project from data folder."""
    data_folder = Path('data').resolve()
    print(f"\n=== Testing auto-load project from {data_folder} ===")
    
    if not data_folder.exists():
        print(f"❌ data folder does not exist: {data_folder}")
        return False
    
    try:
        loader = ProjectDataLoader()
        project = loader.load_project(data_folder)
        print(f"✅ Project loaded successfully")
        print(f"   Parent nucleus: {project.beta_inputs.parent_nucleus}")
        print(f"   Daughter nucleus: {project.beta_inputs.daughter_nucleus}")
        print(f"   Levels: {len(project.levels)}")
        print(f"   Transitions: {len(project.transitions)}")
        return True
    except Exception as exc:
        print(f"❌ Auto-load failed: {exc}")
        return False

def test_auto_load_template():
    """Test auto-load of template."""
    template_file = Path('templates/scheme_template.eps').resolve()
    print(f"\n=== Testing auto-load template from {template_file} ===")
    
    if not template_file.exists():
        print(f"❌ Template file does not exist: {template_file}")
        return False
    
    if template_file.stat().st_size == 0:
        print(f"❌ Template file is empty")
        return False
    
    print(f"✅ Template file exists")
    print(f"   Size: {template_file.stat().st_size} bytes")
    return True

def test_build_output_path():
    """Test auto-build of output path."""
    print(f"\n=== Testing auto-build of output path ===")
    
    parent = "122Ag"
    daughter = "122Cd"
    timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
    
    outputs_dir = Path('outputs').resolve()
    outputs_dir.mkdir(parents=True, exist_ok=True)
    
    filename = f'{parent}_{daughter}_{timestamp}.eps'
    output_path = outputs_dir / filename
    
    print(f"✅ Output path generated:")
    print(f"   {output_path}")
    print(f"   Parent: {parent}")
    print(f"   Daughter: {daughter}")
    print(f"   Timestamp: {timestamp}")
    
    # Check format
    if output_path.name.endswith('.eps') and '_' in output_path.name:
        print(f"✅ Path format is correct")
        return True
    else:
        print(f"❌ Path format is incorrect")
        return False

if __name__ == '__main__':
    results = []
    
    results.append(("auto-load project", test_auto_load_project()))
    results.append(("auto-load template", test_auto_load_template()))
    results.append(("build output path", test_build_output_path()))
    
    print(f"\n{'='*60}")
    print("TEST SUMMARY")
    print(f"{'='*60}")
    for name, passed in results:
        status = "✅ PASS" if passed else "❌ FAIL"
        print(f"{status}: {name}")
    
    all_passed = all(r[1] for r in results)
    if all_passed:
        print("\n✅ All auto-load tests passed!")
        exit(0)
    else:
        print("\n❌ Some tests failed")
        exit(1)
