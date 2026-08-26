import csv

from app_decay_scheme.abf import compute_abf
from app_decay_scheme.loaders import ProjectDataLoader


def test_ods_like_headers_are_mapped_to_abf_fields(tmp_path):
    levels_path = tmp_path / 'levels.csv'
    transitions_path = tmp_path / 'transitions.csv'

    with levels_path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['level_id', 'nucleus', 'E level keV', 'Jpi'])
        writer.writeheader()
        writer.writerow({'level_id': 'lv_0', 'nucleus': '122Cd', 'E level keV': '0.0', 'Jpi': '0+'})
        writer.writerow({'level_id': 'lv_1', 'nucleus': '122Cd', 'E level keV': '100.0', 'Jpi': '2+'})

    with transitions_path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(
            f,
            fieldnames=['transition_id', 'Level Initial ID', 'Level Final ID', 'E Gamma keV', 'Absolute %'],
        )
        writer.writeheader()
        writer.writerow({
            'transition_id': 'tr_1',
            'Level Initial ID': 'lv_1',
            'Level Final ID': 'lv_0',
            'E Gamma keV': '100.0',
            'Absolute %': '12.5',
        })

    project = ProjectDataLoader().load_project(tmp_path)
    rows = compute_abf(project.levels, project.transitions, mode='no_ground_state', field='absolute_percent')

    rows_by_id = {row.level_id: row for row in rows}
    assert rows_by_id['lv_1'].incoming == 0.0
    assert rows_by_id['lv_1'].outgoing == 12.5
    assert rows_by_id['lv_0'].incoming == 12.5
    assert rows_by_id['lv_0'].outgoing == 0.0
