"""Regression tests for Jackson exports omitting null seedMetric values."""
import copy
from decimal import InvalidOperation
import json
from pathlib import Path
import tempfile
import unittest
from uuid import UUID
import test_validate as fixtures
from validate import validate_protocol, validate_run, row_map


class OptionalSeedMetricTest(unittest.TestCase):
    def test_omitted_nt_field_is_valid(self):
        source, protocol = fixtures.fixture()
        self.assertIsNone(source['registrations'][-1].pop('seedMetric'))
        self.assertEqual([], validate_protocol(source, protocol))

    def test_explicit_null_and_omission_have_identical_inputs(self):
        source, _ = fixtures.fixture()
        omitted = copy.deepcopy(source)
        omitted['registrations'][-1].pop('seedMetric')
        self.assertEqual(row_map(source), row_map(omitted))

    def test_zero_is_not_converted_to_nt(self):
        source, _ = fixtures.fixture()
        source['registrations'][0]['seedMetric'] = 0
        rid = source['registrations'][0]['registrationId']
        self.assertEqual(0, row_map(source)[rid][1])
        self.assertIsNotNone(row_map(source)[rid][1])

    def test_invalid_metric_is_still_rejected(self):
        source, _ = fixtures.fixture()
        source['registrations'][0]['seedMetric'] = 'not-a-number'
        with self.assertRaises((InvalidOperation, ValueError)):
            row_map(source)

    def test_missing_numeric_seed_does_not_disable_group_validation(self):
        source, protocol = fixtures.fixture()
        source['registrations'][0].pop('seedMetric')
        self.assertTrue(validate_protocol(source, protocol))

    def test_complete_run_with_omitted_nt_in_input_and_snapshots(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            fixtures.RunWiringTest().make_run(folder)
            manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
            nt_id = str(UUID(int=8))
            for scenario in manifest['scenarios']:
                input_path = folder / scenario['input']
                source = json.loads(input_path.read_text(encoding='utf-8'))
                for row in source['registrations']:
                    if row['registrationId'] == nt_id:
                        row.pop('seedMetric')
                input_path.write_text(json.dumps(source), encoding='utf-8')
                eligible = sorted((r for r in source['registrations'] if r['status'] == 'CONFIRMED'),
                                  key=lambda r: (r.get('seedMetric') is None, r.get('seedMetric', 0)))
                capacity = source['capacity']
                groups = [eligible[i:i+capacity] for i in range(0, len(eligible), capacity)][::-1]
                protocol = dict(unitCount=len(groups), assignmentCount=len(eligible), units=[
                    dict(sequenceNumber=i+1, assignments=[
                        dict(registrationId=r['registrationId'], laneOrPosition=source['positionOrder'][j])
                        for j, r in enumerate(group)]) for i, group in enumerate(groups)])
                for response_name, snapshot_name in zip(scenario['protocols'], scenario['snapshots']):
                    (folder / response_name).write_text(json.dumps(protocol), encoding='utf-8')
                    path = folder / snapshot_name
                    snapshot = json.loads(path.read_text(encoding='utf-8'))
                    for participant in snapshot['input']['participants']:
                        if participant['registrationId'] == nt_id:
                            participant.pop('seedMetric')
                    snapshot['protocol'] = protocol
                    path.write_text(json.dumps(snapshot), encoding='utf-8')
            report = validate_run(folder)
            self.assertTrue(report['passed'], report['violations'])
            self.assertEqual(10, report['checkedProtocols'])


if __name__ == '__main__':
    unittest.main()
