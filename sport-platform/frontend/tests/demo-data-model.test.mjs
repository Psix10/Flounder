import test from 'node:test'
import assert from 'node:assert/strict'
import { buildDemoRequest, isMatchingDemoPlan } from '../src/features/protocol/demo-data-model.mjs'
const id = '00000000-0000-4000-8000-000000000001'
const plan = { eventId: 'event', disciplineId: 'discipline', ruleVersionId: id,
  capacity: 4, count: 8, datasetVersion: 'SWIM_MIXED_V1', state: 'AVAILABLE' }
test('request is bound to displayed rule, capacity and count', () => {
  assert.deepEqual(buildDemoRequest(plan,id,true), { requestId:id, expectedRuleVersionId:id,
    expectedCapacity:4, expectedCount:8, datasetVersion:'SWIM_MIXED_V1', confirmed:true })
})
test('request is frozen and does not carry arbitrary participants or statuses', () => {
  const request=buildDemoRequest(plan,id,true)
  assert.ok(Object.isFrozen(request))
  assert.equal('participants' in request,false)
  assert.equal('status' in request,false)
})
test('confirmation requires literal true', () => {
  for(const value of [false,null,undefined,'true',1]) assert.throws(()=>buildDemoRequest(plan,id,value))
})
test('prepared and blocked plans cannot be submitted', () => {
  for(const state of ['PREPARED','BLOCKED','UNKNOWN']) assert.throws(()=>buildDemoRequest({...plan,state},id,true))
})
test('count must be twice the displayed capacity', () => {
  for(const count of [0,7,9,64]) assert.throws(()=>buildDemoRequest({...plan,count},id,true))
})
test('capacity is bounded and integral', () => {
  for(const capacity of [0,33,1.5,NaN]) assert.throws(()=>buildDemoRequest({...plan,capacity,count:capacity*2},id,true))
})
test('identifiers and dataset version are checked before sending', () => {
  assert.throws(()=>buildDemoRequest(plan,'invalid',true))
  assert.throws(()=>buildDemoRequest({...plan,ruleVersionId:'invalid'},id,true))
  assert.throws(()=>buildDemoRequest({...plan,datasetVersion:'NEW'},id,true))
})
test('preview must refer to the exact event and discipline', () => {
  assert.equal(isMatchingDemoPlan(plan,'event','discipline'),true)
  assert.equal(isMatchingDemoPlan(plan,'other','discipline'),false)
  assert.equal(isMatchingDemoPlan(plan,'event','other'),false)
  assert.equal(isMatchingDemoPlan(null,'event','discipline'),false)
})
