import contextlib
import io
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch
import manual_baseline as manual


class ManualBaselineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.run = self.root/'run'; self.run.mkdir()
        (self.run/'baseline').mkdir()
        self.source = dict(schemaVersion=1,capacity=8,heatOrder='SLOWEST_FIRST',partialHeat='FIRST',
                           ntPlacement='WEAKEST',positionOrder=[4,5,3,6,2,7,1,8],allowedPositions=list(range(1,9)),
                           registrations=[dict(registrationId='00000000-0000-0000-0000-000000000001',status='CONFIRMED',seedMetric=30.25),
                           dict(registrationId='00000000-0000-0000-0000-000000000002',status='CONFIRMED',seedMetric=31),
                           dict(registrationId='00000000-0000-0000-0000-000000000003',status='CONFIRMED')])
        manual.write_json(self.run/'baseline/input.json',self.source)
        manual.write_json(self.run/'manifest.json',dict(label='SELF_TEST_ONLY',scenarios=[dict(name='baseline',input='baseline/input.json')]))
        manual.write_json(self.run/'validation-report.json',dict(passed=True,violationCount=0))
        self.work = self.root/'work'
        with contextlib.redirect_stdout(io.StringIO()):
            manual.prepare(self.run,self.work)
        self.aliases = manual.load(self.work/'identity-map.json')
        self.valid = b'participant;heat;lane\nP0001;1;4\nP0002;1;5\nP0003;1;3\n'

    def test_preparation_preserves_source_bytes(self):
        self.assertEqual((self.run/'baseline/input.json').read_bytes(),(self.work/'input.json').read_bytes())
        self.assertFalse(list(self.work.glob('protocol*.json')))
        self.assertIn('NT',(self.work/'participants.csv').read_text(encoding='utf-8-sig'))
        rows = (self.work/'assignments.csv').read_text(encoding='utf-8-sig')
        self.assertIn('P0001;;',rows)

    def test_existing_output_is_not_overwritten(self):
        before = (self.work/'assignments.csv').read_bytes()
        with self.assertRaises(ValueError):
            manual.prepare(self.run,self.work)
        self.assertEqual(before,(self.work/'assignments.csv').read_bytes())

    def test_failed_source_run_is_rejected(self):
        manual.write_json(self.run/'validation-report.json',dict(passed=False,violationCount=1))
        with self.assertRaises(ValueError):
            manual.prepare(self.run,self.root/'second')
        self.assertFalse((self.root/'second').exists())

    def test_conversion_preserves_alias_identity(self):
        protocol = manual.convert_assignments(self.valid,self.aliases)
        self.assertEqual(3,protocol['assignmentCount'])
        self.assertEqual(self.aliases['P0001'],protocol['units'][0]['assignments'][0]['registrationId'])
        self.assertEqual([4,5,3],[a['laneOrPosition'] for a in protocol['units'][0]['assignments']])

    def test_blank_assignment_is_not_silently_dropped(self):
        with self.assertRaises(ValueError):
            manual.convert_assignments(b'participant;heat;lane\nP0001;;\n',self.aliases)

    def test_unknown_alias_is_rejected(self):
        with self.assertRaises(ValueError):
            manual.convert_assignments(b'participant;heat;lane\nP9999;1;4\n',self.aliases)

    def test_noninteger_lane_is_rejected(self):
        with self.assertRaises(ValueError):
            manual.convert_assignments(b'participant;heat;lane\nP0001;1;4.0\n',self.aliases)

    def test_duplicates_are_forwarded_to_validator_not_hidden(self):
        attempt = self.work/'test-attempt'; attempt.mkdir()
        raw = b'participant;heat;lane\nP0001;1;4\nP0001;1;5\n'
        seen = []
        def validator(source,protocol):
            seen.append(protocol['assignmentCount'])
            return ['Duplicate assignments']
        report = manual.assess(self.work,attempt,raw,dict(elapsedSeconds=2.5),validator)
        self.assertEqual([2],seen)
        self.assertFalse(report['passed'])
        self.assertEqual(raw,(attempt/'assignments.csv').read_bytes())

    def test_timing_uses_elapsed_counter_and_saves_submitted_bytes(self):
        (self.work/'assignments.csv').write_bytes(self.valid)
        fake = types.SimpleNamespace(validate_protocol=lambda source,protocol: [])
        with patch.dict('sys.modules',{'validate':fake}), patch('builtins.input',side_effect=['','']), \
                patch.object(manual.time,'perf_counter_ns',side_effect=[1_000_000_000,3_500_000_000]), \
                contextlib.redirect_stdout(io.StringIO()):
            code = manual.measure(self.work,'test-operator','UNIT_TEST_ONLY')
        self.assertEqual(0,code)
        folder = self.work/'attempts/attempt-001'
        self.assertEqual(2.5,manual.load(folder/'report.json')['elapsedSeconds'])
        self.assertEqual(self.valid,(folder/'assignments.csv').read_bytes())

    def test_cancellation_does_not_create_success_result(self):
        with patch('builtins.input',side_effect=KeyboardInterrupt),contextlib.redirect_stdout(io.StringIO()):
            code = manual.measure(self.work,'test-operator','UNIT_TEST_ONLY')
        self.assertEqual(130,code)
        report = manual.load(self.work/'attempts/attempt-001/report.json')
        self.assertTrue(report['cancelled'])
        self.assertNotIn('passed',report)


if __name__ == '__main__':
    unittest.main()
