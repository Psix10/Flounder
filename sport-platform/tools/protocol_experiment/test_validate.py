import copy
import unittest
import json
import tempfile
from pathlib import Path
from uuid import UUID
from validate import validate_protocol, validate_run

ORDER = [4, 5, 3, 6, 2, 7, 1, 8]


def fixture():
    rows = [{'registrationId': str(UUID(int=i)), 'status': 'CONFIRMED',
             'seedMetric': 20 + i if i < 9 else 28 if i == 9 else None} for i in range(1, 11)]
    source = dict(schemaVersion=1, capacity=8, heatOrder='SLOWEST_FIRST', partialHeat='FIRST',
                  ntPlacement='WEAKEST', positionOrder=ORDER[:], allowedPositions=list(range(1, 9)), registrations=rows)
    def heat(sequence, indices):
        return dict(sequenceNumber=sequence, assignments=[
            dict(registrationId=rows[index - 1]['registrationId'], laneOrPosition=ORDER[n])
            for n, index in enumerate(indices)])
    protocol = dict(unitCount=2, assignmentCount=10, units=[heat(1, [9, 10]), heat(2, list(range(1, 9)))])
    return source, protocol


class ValidatorTest(unittest.TestCase):
    def setUp(self):
        self.source, self.protocol = fixture()
    def bad(self):
        self.assertTrue(validate_protocol(self.source, self.protocol))
    def test_valid_fixture(self):
        self.assertEqual([], validate_protocol(self.source, self.protocol))
    def test_missing_registration(self):
        self.protocol['units'][0]['assignments'].pop()
        self.protocol['assignmentCount'] = 9
        self.bad()
    def test_duplicate_registration(self):
        self.protocol['units'][0]['assignments'][1]['registrationId'] = str(UUID(int=1))
        self.bad()
    def test_unknown_registration(self):
        self.protocol['units'][0]['assignments'][0]['registrationId'] = str(UUID(int=999))
        self.bad()
    def test_ineligible_registration(self):
        self.source['registrations'][0]['status'] = 'REJECTED'
        self.bad()
    def test_lane_outside_allowed(self):
        self.protocol['units'][0]['assignments'][0]['laneOrPosition'] = 99
        self.bad()
    def test_duplicate_lane(self):
        self.protocol['units'][0]['assignments'][1]['laneOrPosition'] = 4
        self.bad()
    def test_wrong_center_order(self):
        a = self.protocol['units'][1]['assignments']
        a[0]['laneOrPosition'], a[1]['laneOrPosition'] = a[1]['laneOrPosition'], a[0]['laneOrPosition']
        self.bad()
    def test_wrong_heat_grouping(self):
        a, b = [u['assignments'][0] for u in self.protocol['units']]
        a['registrationId'], b['registrationId'] = b['registrationId'], a['registrationId']
        self.bad()
    def test_partial_heat_not_first(self):
        self.protocol['units'][0]['sequenceNumber'] = 2
        self.protocol['units'][1]['sequenceNumber'] = 1
        self.bad()
    def test_nt_before_numeric(self):
        a = self.protocol['units'][0]['assignments']
        a[0]['registrationId'], a[1]['registrationId'] = a[1]['registrationId'], a[0]['registrationId']
        self.bad()
    def test_count_mismatch(self):
        self.protocol['assignmentCount'] = 11
        self.bad()
    def test_over_capacity(self):
        self.protocol['units'][0]['assignments'].extend(copy.deepcopy(self.protocol['units'][1]['assignments'][:7]))
        self.bad()
    def test_equal_metric_identity_swap_is_valid(self):
        a = self.protocol['units'][0]['assignments'][0]
        b = self.protocol['units'][1]['assignments'][-1]
        a['registrationId'], b['registrationId'] = b['registrationId'], a['registrationId']
        self.assertEqual([], validate_protocol(self.source, self.protocol))
    def test_unsupported_rule_does_not_silently_pass(self):
        self.source['heatOrder'] = 'FASTEST_FIRST'
        self.bad()


class RunWiringTest(unittest.TestCase):
    def write(self, folder, path, value):
        target = folder / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(value), encoding='utf-8')
    def make_run(self, folder):
        # Deliberately synthetic unit-test artifacts, never experimental measurements.
        rows = [{'registrationId': str(UUID(int=i)), 'status': 'CONFIRMED', 'seedMetric': 20 + i}
                for i in range(1, 9)]
        scenarios, measurements = [], ['scenario,repetition,operation,duration_ms,unit_count,assignment_count,revision']
        revision = 0
        for name in ['baseline', 'no_change', 'addition', 'withdrawal', 'seed_change']:
            if name == 'addition':
                rows.extend({'registrationId': str(UUID(int=i)), 'status': 'CONFIRMED', 'seedMetric': 20 + i}
                            for i in range(9, 12))
            if name == 'withdrawal':
                for row in rows[:2]:
                    row['status'] = 'REJECTED'
            if name == 'seed_change':
                rows[4]['seedMetric'] = 99.99
            source = dict(schemaVersion=1, capacity=8, heatOrder='SLOWEST_FIRST', partialHeat='FIRST',
                          ntPlacement='WEAKEST', positionOrder=ORDER, allowedPositions=list(range(1,9)),
                          registrations=copy.deepcopy(rows))
            self.write(folder, name + '/input.json', source)
            eligible = sorted((r for r in rows if r['status'] == 'CONFIRMED'), key=lambda r: r['seedMetric'])
            groups = [eligible[i:i+8] for i in range(0, len(eligible), 8)][::-1]
            protocol = dict(unitCount=len(groups), assignmentCount=len(eligible), units=[
                dict(sequenceNumber=index+1, assignments=[dict(registrationId=r['registrationId'], laneOrPosition=ORDER[j])
                     for j,r in enumerate(group)]) for index, group in enumerate(groups)])
            scenario = dict(name=name, input=name+'/input.json', protocols=[], snapshots=[])
            for rep in range(1,3):
                revision += 1
                operation = 'GENERATE' if revision == 1 else 'REGENERATE'
                response_path, audit_path = f'{name}/protocol-{rep:02}.json', f'{name}/snapshot-{rep:02}.json'
                self.write(folder, response_path, protocol)
                self.write(folder, audit_path, dict(revision=revision, operation=operation, ruleSetVersionId='self-test-version',
                           input=dict(ruleSetVersionId='self-test-version', participants=[
                               dict(registrationId=r['registrationId'], seedMetric=r['seedMetric']) for r in eligible]),
                           protocol=protocol))
                scenario['protocols'].append(response_path)
                scenario['snapshots'].append(audit_path)
                measurements.append(f'{name},{rep},{operation},1.0,{len(groups)},{len(eligible)},{revision}')
            scenarios.append(scenario)
        self.write(folder, 'manifest.json', dict(label='SELF_TEST_ONLY', baseSize=8, repetitions=2,
                   ruleSetVersionId='self-test-version', timingScope='FAKE_UNIT_TEST_TIMING', scenarios=scenarios))
        (folder/'measurements.csv').write_text('\n'.join(measurements)+'\n', encoding='utf-8')
    def test_valid_complete_run(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory); self.make_run(folder)
            report = validate_run(folder)
            self.assertTrue(report['passed'], report['violations'])
            self.assertEqual(10, report['checkedProtocols'])
    def test_snapshot_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory); self.make_run(folder)
            path = folder/'baseline/snapshot-01.json'
            audit = json.loads(path.read_text(encoding='utf-8'))
            audit['input']['participants'][0]['seedMetric'] = 999
            self.write(folder, 'baseline/snapshot-01.json', audit)
            report = validate_run(folder)
            self.assertFalse(report['passed'])
            self.assertTrue(any('snapshot participants' in error for error in report['violations']))
    def test_missing_measurement_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory); self.make_run(folder)
            path = folder/'measurements.csv'
            lines = path.read_text(encoding='utf-8').splitlines()
            path.write_text('\n'.join(lines[:-1])+'\n', encoding='utf-8')
            self.assertFalse(validate_run(folder)['passed'])


if __name__ == '__main__':
    unittest.main()
