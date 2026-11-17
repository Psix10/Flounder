import test from 'node:test'
import assert from 'node:assert/strict'
import { assertUnitSnapshot, mutateAndReadUnit } from '../src/features/results/mutation-readback.mjs'
const unit = (status='DRAFT') => ({ id:'unit', status, entries:[{entryId:'entry',rawValue:'01:03.25'}] })
test('void publication response is ignored and published state is read separately',async()=>{
  const calls=[]
  const result=await mutateAndReadUnit(async()=>{calls.push('POST');return undefined},async()=>{calls.push('GET');return unit('PUBLISHED')},'unit','PUBLISHED')
  assert.equal(result.status,'PUBLISHED');assert.deepEqual(calls,['POST','GET'])
})
test('void recalculation response is ignored and actual places come from GET',async()=>{
  const actual=unit();actual.entries[0].finalPlace=1
  const result=await mutateAndReadUnit(async()=>undefined,async()=>actual,'unit','DRAFT')
  assert.equal(result.entries[0].finalPlace,1)
})
test('void record response does not require a result object',async()=>{
  const result=await mutateAndReadUnit(async()=>undefined,async()=>unit(),'unit','DRAFT')
  assert.equal(result.entries[0].rawValue,'01:03.25')
})
test('null and differently shaped mutation response bodies are not used as snapshots',async()=>{
  for(const response of [null,{},'OK',{competitionUnitEntryId:'entry'}]){
    const result=await mutateAndReadUnit(async()=>response,async()=>unit(),'unit','DRAFT')
    assert.equal(result.id,'unit')
  }
})
test('mutation rejection is not automatically retried',async()=>{
  let posts=0,reads=0
  await assert.rejects(mutateAndReadUnit(async()=>{posts++;throw new Error('rejected')},async()=>{reads++;return unit()},'unit'),/rejected/)
  assert.equal(posts,1);assert.equal(reads,0)
})
test('read failure after acknowledged mutation does not resend mutation',async()=>{
  let posts=0,reads=0
  await assert.rejects(mutateAndReadUnit(async()=>{posts++},async()=>{reads++;throw new Error('offline')},'unit'),/Не повторяйте запись/)
  assert.equal(posts,1);assert.equal(reads,1)
})
test('missing GET body produces an explicit read error instead of undefined.id',async()=>{
  await assert.rejects(mutateAndReadUnit(async()=>undefined,async()=>undefined,'unit'),/API чтения/)
})
test('snapshot for a different unit is rejected',()=>{
  assert.throws(()=>assertUnitSnapshot(unit(),'other'),/API чтения/)
})
test('snapshot without entries is rejected',()=>{
  assert.throws(()=>assertUnitSnapshot({id:'unit'},'unit'),/API чтения/)
})
test('unexpected publication state stops subsequent operations',async()=>{
  await assert.rejects(mutateAndReadUnit(async()=>undefined,async()=>unit('DRAFT'),'unit','PUBLISHED'),/вместо ожидаемого/)
})
