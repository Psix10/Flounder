import contextlib
import copy
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import manual_updates as u

ORDER=[4,5,3,6,2,7,1,8]

class ManualUpdateTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.run=self.root/'run';self.run.mkdir()
        self.source=dict(schemaVersion=1,capacity=8,heatOrder='SLOWEST_FIRST',partialHeat='FIRST',ntPlacement='WEAKEST',
                         positionOrder=ORDER,allowedPositions=list(range(1,9)),registrations=[
            dict(registrationId=f'id-{i}',status='CONFIRMED',seedMetric=i) for i in range(1,9)])
        addition=copy.deepcopy(self.source)
        addition['registrations'] += [dict(registrationId=f'id-{i}',status='CONFIRMED',seedMetric=i) for i in range(9,12)]
        withdrawal=copy.deepcopy(addition)
        for r in withdrawal['registrations'][:2]:r['status']='REJECTED'
        changed=copy.deepcopy(withdrawal);changed['registrations'][4]['seedMetric']=99.99
        self.sources=dict(baseline=self.source,addition=addition,withdrawal=withdrawal,seed_change=changed)
        for name,data in self.sources.items():u.save(self.run/f'{name}.json',data)
        u.save(self.run/'manifest.json',dict(label='SELF_TEST_ONLY',baseSize=8,
            scenarios=[dict(name=n,input=n+'.json') for n in self.sources]))
        u.save(self.run/'validation-report.json',dict(passed=True,violationCount=0))
        self.baseline=self.root/'baseline';self.baseline.mkdir()
        u.save(self.baseline/'task.json',dict(scope='INITIAL_GENERATION_ONLY'))
        u.save(self.baseline/'input.json',self.source)
        self.aliases={f'P{i:04d}':f'id-{i}' for i in range(1,9)}
        u.save(self.baseline/'identity-map.json',self.aliases)
        self.prior=self.baseline/'attempts/attempt-001';self.prior.mkdir(parents=True)
        u.save(self.prior/'report.json',dict(passed=True))
        u.write_csv(self.prior/'assignments.csv',['participant','heat','lane'],[
            dict(participant=f'P{i:04d}',heat=1,lane=ORDER[i-1]) for i in range(1,9)])
        self.validator=patch.object(u,'check_protocol',return_value=[]).start()
        self.addCleanup(patch.stopall)
    def prepare(self,name,prior=None):
        prior=self.prior if prior is None else prior
        with contextlib.redirect_stdout(io.StringIO()):
            return u.prepare(self.run,prior,name,self.root/name)
    def accept_test_fixture(self,work):
        source=u.load(work/'input.json');aliases=u.load(work/'identity-map.json');reverse={v:k for k,v in aliases.items()}
        eligible=sorted([r for r in source['registrations'] if r['status']=='CONFIRMED'],key=lambda r:r.get('seedMetric',0))
        groups=[eligible[i:i+8] for i in range(0,len(eligible),8)][::-1]
        data=[dict(participant=reverse[r['registrationId']],heat=i+1,lane=ORDER[j])
              for i,group in enumerate(groups) for j,r in enumerate(group)]
        attempt=work/'attempts/attempt-001';attempt.mkdir(parents=True)
        u.write_csv(attempt/'assignments.csv',['participant','heat','lane'],data)
        u.save(attempt/'report.json',dict(passed=True))
        return attempt
    def test_addition_preserves_previous_rows_and_adds_blank_rows(self):
        work=self.prepare('addition');data=u.read_csv((work/'assignments.csv').read_bytes())
        self.assertEqual(11,len(data));self.assertEqual('4',data[0]['lane'])
        self.assertTrue(all(r['heat']==r['lane']=='' for r in data[8:]))
        self.assertEqual('id-9',u.load(work/'identity-map.json')['P0009'])
    def test_failed_previous_report_is_rejected(self):
        u.save(self.prior/'report.json',dict(passed=False))
        with self.assertRaises(ValueError):self.prepare('addition')
        self.assertFalse((self.root/'addition').exists())
    def test_mismatched_previous_input_is_rejected(self):
        other=copy.deepcopy(self.source);other['registrations'][0]['seedMetric']=100
        u.save(self.baseline/'input.json',other)
        with self.assertRaises(ValueError):self.prepare('addition')
    def test_existing_output_is_not_overwritten(self):
        work=self.prepare('addition');original=(work/'assignments.csv').read_bytes()
        with self.assertRaises(ValueError):self.prepare('addition')
        self.assertEqual(original,(work/'assignments.csv').read_bytes())
    def test_withdrawal_keeps_old_rows_for_human_removal(self):
        added=self.prepare('addition');accepted=self.accept_test_fixture(added)
        work=self.prepare('withdrawal',accepted)
        submitted=u.read_csv((work/'assignments.csv').read_bytes())
        self.assertEqual(11,len(submitted))
        self.assertEqual(9,u.load(work/'task.json')['participantCount'])
        self.assertTrue({'P0001','P0002'}<={r['participant'] for r in submitted})
    def test_seed_change_does_not_compute_new_assignments(self):
        added=self.prepare('addition');p=self.accept_test_fixture(added)
        removed=self.prepare('withdrawal',p);p=self.accept_test_fixture(removed)
        work=self.prepare('seed_change',p)
        self.assertEqual((p/'assignments.csv').read_bytes().decode('utf-8-sig').replace('\r\n','\n'),
                         (work/'assignments.csv').read_bytes().decode('utf-8-sig').replace('\r\n','\n'))
        self.assertEqual(1,u.load(work/'task.json')['changeCount'])
    def test_wrong_scenario_sequence_is_rejected(self):
        with self.assertRaises(ValueError):self.prepare('withdrawal')
    def test_duplicate_rows_are_not_silently_removed(self):
        raw=b'participant;heat;lane\nP0001;1;4\nP0001;1;5\n'
        self.assertEqual(2,u.convert(raw,self.aliases)['assignmentCount'])
    def test_saved_previous_csv_is_revalidated(self):
        self.validator.return_value=['Invalid previous protocol']
        with self.assertRaises(ValueError):self.prepare('addition')
        self.assertFalse((self.root/'addition').exists())
    def test_measure_saves_update_scope_timing_and_submitted_bytes(self):
        work=self.prepare('addition');accepted=self.accept_test_fixture(work)
        raw=(accepted/'assignments.csv').read_bytes();(work/'assignments.csv').write_bytes(raw)
        with patch('builtins.input',side_effect=['','']),patch.object(u.time,'perf_counter_ns',side_effect=[1_000_000_000,4_000_000_000]),contextlib.redirect_stdout(io.StringIO()):
            code=u.measure(work,'test-operator','UNIT_TEST_ONLY')
        self.assertEqual(0,code)
        folder=work/'attempts/attempt-002';report=u.load(folder/'report.json')
        self.assertEqual('addition',report['scenario']);self.assertEqual(3,report['elapsedSeconds'])
        self.assertEqual(raw,(folder/'assignments.csv').read_bytes())

if __name__=='__main__':unittest.main()
