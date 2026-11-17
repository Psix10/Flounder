import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createDrafts, draftFromEntry, sameDraft, isDirty, dirtyIds, validateDraft,
  buildSavePlan, mergeDrafts, applyResultResponse, saveSequentially, canEditEntry,
  isComplete, inferResultType,
} from '../src/features/results/judge-drafts.mjs'
const entry = (id, rawValue = null, resultStatus = null, resultType = null) => ({
  entryId: id, participantName: `Athlete ${id}`, registrationId: `reg-${id}`,
  laneOrPosition: Number(id) || 1, rawValue, resultStatus, resultType, finalPlace: null,
})
const empty = [entry('1'), entry('2'), entry('3'), entry('4')]
const normal = rawValue => ({ rawValue, status: 'PENDING', resultType: 'TIME' })

test('blank loaded rows are clean', () => assert.deepEqual(dirtyIds(empty,createDrafts(empty)),[]))
test('entering one result marks exactly one row', () => {
  const drafts=createDrafts(empty); drafts['2']=normal('01:03.25')
  assert.deepEqual(dirtyIds(empty,drafts),['2'])
})
test('changing input type alone does not make untouched empty rows dirty', () => {
  const drafts=Object.fromEntries(Object.entries(createDrafts(empty)).map(([id,d])=>[id,{...d,resultType:'POINTS'}]))
  assert.deepEqual(dirtyIds(empty,drafts,'POINTS'),[])
  drafts['1'].rawValue='12.5'
  assert.equal(buildSavePlan(empty,drafts,'POINTS').changes.length,1)
})
test('VALID result maps to ordinary editable input without a false dirty flag', () => {
  const e=entry('1','01:03.25','VALID','TIME')
  assert.equal(draftFromEntry(e).status,'PENDING')
  assert.equal(isDirty(e,draftFromEntry(e)),false)
})
test('clock padding and surrounding whitespace do not cause unnecessary writes', () => {
  assert.equal(sameDraft(normal('01:03.25'),normal(' 001:03.25 ')),true)
})
test('points canonicalization retains decimal precision without rounding', () => {
  const a={rawValue:'00012345678901234567890.1200',status:'PENDING',resultType:'POINTS'}
  assert.equal(sameDraft(a,{...a,rawValue:'12345678901234567890.12'}),true)
  assert.equal(sameDraft(a,{...a,rawValue:'12345678901234567891.12'}),false)
})
test('zero and signed zero points compare consistently', () => {
  const a={rawValue:'-000.00',status:'PENDING',resultType:'POINTS'}
  assert.equal(sameDraft(a,{...a,rawValue:'+0'}),true)
})
test('single-row save plan does not include other dirty rows', () => {
  const drafts=createDrafts(empty); drafts['1']=normal('01:03.25');drafts['2']=normal('01:04.00')
  const plan=buildSavePlan(empty,drafts,'TIME',['1'])
  assert.deepEqual(plan.changes.map(c=>c.entryId),['1'])
  assert.equal(drafts['2'].rawValue,'01:04.00')
})
test('saving row 1 and reading all rows keeps unsaved row 2 and row 3', () => {
  const drafts=createDrafts(empty)
  drafts['1']=normal('01:03.25'); drafts['2']=normal('01:04.00');drafts['3']=normal('01:05.00')
  const after=[entry('1','01:03.25','PENDING','TIME'),...empty.slice(1)]
  const merged=mergeDrafts(empty,after,drafts)
  assert.deepEqual(dirtyIds(after,merged.drafts),['2','3'])
  assert.equal(merged.drafts['2'].rawValue,'01:04.00')
  assert.equal(merged.drafts['3'].rawValue,'01:05.00')
  assert.deepEqual(merged.conflicts,[])
})
test('remote value matching an uncertain request becomes clean without another write', () => {
  const drafts=createDrafts(empty);drafts['1']=normal('01:03.25')
  const after=[entry('1','01:03.25','PENDING','TIME'),...empty.slice(1)]
  assert.deepEqual(dirtyIds(after,mergeDrafts(empty,after,drafts).drafts),[])
})
test('remote conflict preserves the local draft and reports the row', () => {
  const before=[entry('1','01:03.25','VALID','TIME')]
  const drafts={'1':normal('01:02.00')}
  const after=[entry('1','01:04.00','PENDING','TIME')]
  const merged=mergeDrafts(before,after,drafts)
  assert.equal(merged.drafts['1'].rawValue,'01:02.00')
  assert.deepEqual(merged.conflicts,['1'])
})
test('unchanged rows take new server state', () => {
  const after=[entry('1','01:01.00','VALID','TIME'),...empty.slice(1)]
  const merged=mergeDrafts(empty,after,createDrafts(empty))
  assert.equal(merged.drafts['1'].rawValue,'01:01.00')
  assert.deepEqual(dirtyIds(after,merged.drafts),[])
})
test('new server rows are initialized without erasing existing drafts', () => {
  const before=empty.slice(0,1), drafts={'1':normal('01:03.25')}
  const after=[...before,entry('2')]
  const merged=mergeDrafts(before,after,drafts)
  assert.equal(merged.drafts['1'].rawValue,'01:03.25')
  assert.equal(merged.drafts['2'].rawValue,'')
})
test('removed dirty rows are retained and reported as orphaned', () => {
  const drafts=createDrafts(empty); drafts['4']=normal('01:03.25')
  const merged=mergeDrafts(empty,empty.slice(0,3),drafts)
  assert.deepEqual(merged.orphaned,['4'])
  assert.equal(merged.drafts['4'].rawValue,'01:03.25')
})
test('all validation is completed before exposing any save changes', () => {
  const drafts=createDrafts(empty); drafts['1']=normal('01:03.25');drafts['2']=normal('bad')
  const plan=buildSavePlan(empty,drafts)
  assert.deepEqual(plan.changes,[])
  assert.ok(plan.errors['2'])
})
test('clearing an existing normal result requires correction, not silent deletion', () => {
  const e=entry('1','01:03.25','VALID','TIME')
  const plan=buildSavePlan([e],{'1':normal('')})
  assert.ok(plan.errors['1'])
})
test('clock input rejects invalid seconds and missing hundredths', () => {
  for(const raw of ['01:60.00','01:03','01:03.2','NaN','Infinity','-01:03.25','1:03.25']) assert.ok(validateDraft(normal(raw)))
  assert.equal(validateDraft(normal('01:03.25')),null)
})
test('POINTS accepts finite signed decimals and rejects comma or infinity', () => {
  const points=rawValue=>({rawValue,status:'PENDING',resultType:'POINTS'})
  for(const raw of ['0','-1.5','+12.25']) assert.equal(validateDraft(points(raw)),null)
  for(const raw of ['1,5','Infinity','1e999','hello']) assert.ok(validateDraft(points(raw)))
})
test('special status needs no time and does not submit a stale numeric value', () => {
  const drafts=createDrafts(empty);drafts['1']={...normal('01:03.25'),status:'DID_NOT_START'}
  const plan=buildSavePlan(empty,drafts)
  assert.deepEqual(plan.changes[0].payload,{competitionUnitEntryId:'1',rawValue:'',status:'DID_NOT_START'})
})
test('special status comparison ignores an unused local time', () => {
  const a={...normal('01:03.25'),status:'DID_NOT_FINISH'}
  assert.equal(sameDraft(a,{...a,rawValue:''}),true)
})
test('normal save does not send a fake VALID status', () => {
  const plan=buildSavePlan([empty[0]],{'1':normal(' 01:03.25 ')})
  assert.deepEqual(plan.changes[0].payload,{competitionUnitEntryId:'1',rawValue:'01:03.25'})
})
test('CORRECTED EXHIBITION and unknown statuses are not silently rewritten', () => {
  for(const status of ['CORRECTED','EXHIBITION','NEW_SERVER_STATUS']) {
    const e=entry('1','01:03.25',status,'TIME')
    assert.equal(canEditEntry(e),false)
    assert.equal(buildSavePlan([e],{'1':normal('01:02.00')}).changes.length,0)
  }
})
test('result type is inferred from saved results and mixed types are detected', () => {
  assert.deepEqual(inferResultType([entry('1','12','VALID','POINTS')]),{type:'POINTS',mixed:false})
  assert.equal(inferResultType([entry('1','12','VALID','POINTS'),entry('2','01:03.25','VALID','TIME')]).mixed,true)
})
test('changing a saved normal result type requires an explicit write', () => {
  const e=entry('1','12','VALID','POINTS')
  assert.equal(isDirty(e,{...normal('00:12.00')}),true)
})
test('result response updates only the acknowledged entry', () => {
  const unit={id:'unit',entries:empty}
  const next=applyResultResponse(unit,{competitionUnitEntryId:'1',rawValue:'01:03.25',resultType:'TIME',status:'PENDING',finalPlace:null})
  assert.equal(next.entries[0].rawValue,'01:03.25')
  assert.equal(next.entries[1],empty[1])
  assert.equal(unit.entries[0].rawValue,null)
})
test('response for another row set is rejected', () => {
  assert.throws(()=>applyResultResponse({entries:empty},{competitionUnitEntryId:'other'}))
})
test('saved result completion counts DNS DNF DSQ without values', () => {
  for(const status of ['DID_NOT_START','DID_NOT_FINISH','DISQUALIFIED']) assert.equal(isComplete(entry('1','',status,'TIME')),true)
  assert.equal(isComplete(entry('1')),false)
})
test('batch saving is sequential, not parallel', async () => {
  const calls=[], changes=[{entryId:'1'},{entryId:'2'},{entryId:'3'}]
  let active=0
  const outcome=await saveSequentially(changes,async change=>{
    active++;assert.equal(active,1);calls.push(change.entryId);await Promise.resolve();active--
  })
  assert.deepEqual(calls,['1','2','3']);assert.deepEqual(outcome.savedIds,['1','2','3'])
})
test('batch stops on first error and does not automatically retry', async () => {
  const calls=[], failure=new Error('network')
  const changes=[{entryId:'1'},{entryId:'2'},{entryId:'3'}]
  const outcome=await saveSequentially(changes,async change=>{
    calls.push(change.entryId);if(change.entryId==='2')throw failure
  })
  assert.deepEqual(calls,['1','2']);assert.deepEqual(outcome.savedIds,['1'])
  assert.equal(outcome.failedId,'2');assert.equal(outcome.error,failure)
  assert.deepEqual(outcome.remainingIds,['2','3'])
})
test('unmount/cancellation stops sending future rows', async () => {
  const calls=[], changes=[{entryId:'1'},{entryId:'2'}];let mounted=true
  const outcome=await saveSequentially(changes,async change=>{calls.push(change.entryId);mounted=false},()=>mounted)
  assert.deepEqual(calls,['1']);assert.equal(outcome.stopped,true)
  assert.deepEqual(outcome.remainingIds,['2'])
})
test('save plan freezes the submitted values independently of later edits', () => {
  const drafts={'1':normal('01:03.25')}, plan=buildSavePlan([empty[0]],drafts)
  drafts['1'].rawValue='01:05.00'
  assert.equal(plan.changes[0].payload.rawValue,'01:03.25')
  assert.ok(Object.isFrozen(plan.changes[0].payload))
})

test('a newly protected remote row retains a conflicting draft across repeated reads', () => {
  const before=[entry('1','01:03.25','VALID','TIME')]
  const remote=[entry('1','01:04.00','CORRECTED','TIME')]
  const first=mergeDrafts(before,remote,{'1':normal('01:02.00')})
  const second=mergeDrafts(remote,remote,first.drafts)
  assert.equal(second.drafts['1'].rawValue,'01:02.00')
  assert.deepEqual(second.conflicts,['1'])
})
test('a protected row loaded without local edits is not a conflict', () => {
  const remote=[entry('1','01:04.00','CORRECTED','TIME')]
  const merged=mergeDrafts(remote,remote,createDrafts(remote))
  assert.deepEqual(merged.conflicts,[])
})
test('an orphaned dirty draft survives a second server refresh', () => {
  const before=empty, after=empty.slice(0,3), drafts=createDrafts(empty)
  drafts['4']=normal('01:03.25')
  const first=mergeDrafts(before,after,drafts)
  const second=mergeDrafts(after,after,first.drafts)
  assert.deepEqual(second.orphaned,['4'])
  assert.equal(second.drafts['4'].rawValue,'01:03.25')
})
