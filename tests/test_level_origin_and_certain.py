import csv

from app_decay_scheme.eps_template import _level_style
from app_decay_scheme.loaders import ProjectDataLoader


def test_level_origin_and_certain_are_loaded_and_rendered(tmp_path):
    levels_path = tmp_path / 'levels.csv'
    transitions_path = tmp_path / 'transitions.csv'

    with levels_path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['level_id', 'nucleus', 'E_level_keV', 'level origin', 'certain'])
        writer.writeheader()
        writer.writerow({'level_id': 'lv_known', 'nucleus': '122Cd', 'E_level_keV': '0.0', 'level origin': 'known', 'certain': 'yes'})
        writer.writerow({'level_id': 'lv_new', 'nucleus': '122Cd', 'E_level_keV': '100.0', 'level origin': 'new', 'certain': 'no'})
        writer.writerow({'level_id': 'lv_other', 'nucleus': '122Cd', 'E_level_keV': '200.0', 'level origin': 'candidate', 'certain': 'yes'})

    with transitions_path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['transition_id', 'level_initial_id', 'level_final_id', 'e_gamma_keV', 'absolute_percent'])
        writer.writeheader()
        writer.writerow({'transition_id': 'tr_1', 'level_initial_id': 'lv_known', 'level_final_id': 'lv_new', 'e_gamma_keV': '10.0', 'absolute_percent': '5.0'})

    project = ProjectDataLoader().load_project(tmp_path)
    by_id = {level.level_id: level for level in project.levels}

    assert by_id['lv_known'].level_origin == 'known'
    assert by_id['lv_known'].certain is True
    assert by_id['lv_new'].level_origin == 'new'
    assert by_id['lv_new'].certain is False
    assert by_id['lv_other'].level_origin == 'candidate'
    assert by_id['lv_other'].certain is True

    assert _level_style(by_id['lv_known']) == (0, 0)
    assert _level_style(by_id['lv_new']) == (1, 1)
    assert _level_style(by_id['lv_other']) == (0, 2)
