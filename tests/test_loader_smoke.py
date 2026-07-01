from pathlib import Path
from app_decay_scheme.loaders import ProjectDataLoader

def test_loader_smoke():
    project = ProjectDataLoader().load_project(Path(__file__).resolve().parents[1] / "example_data")
    assert len(project.levels) > 0
    assert len(project.transitions) > 0
    assert project.beta_inputs.parent_nucleus == "122Ag"
