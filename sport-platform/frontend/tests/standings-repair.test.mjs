import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isMissingFinal,isUncertainFailure,protocolErrorMessage,createConfirmationAttempt,
  createRevocationAttempt,unitsReady,canConfirm,filterCategory,categoryOptions,ALL_CATEGORIES,LEGACY_CATEGORY,
} from '../src/features/results/protocol-model.mjs'
const id='12345678-1234-4234-8234-123456789012'
const flags=()=>({permission:true,loading:false,busy:false,finalKnown:true,otherPending:false,pending:false,active:false,unitsReady:true,complete:true})
test('only typed 404 means no active snapshot',()=>{
  assert.equal(isMissingFinal({status:404,code:'results.final_not_found'}),true)
  assert.equal(isMissingFinal({status:404}),false)
  assert.equal(isMissingFinal({status:404,code:'request_error'}),false)
})
test('network failures retain uncertain attempt',()=>assert.equal(isUncertainFailure(new Error('network')),true))
test('5xx responses retain uncertain attempt',()=>assert.equal(isUncertainFailure({status:503}),true))
test('timeouts and rate limits retain attempt',()=>{
  assert.equal(isUncertainFailure({status:408}),true);assert.equal(isUncertainFailure({status:429}),true)
})
test('definite client rejection permits correction',()=>{
  for(const status of [400,401,403,404,409,422]) assert.equal(isUncertainFailure({status}),false)
})
test('confirmation requires explicit true',()=>{
  assert.throws(()=>createConfirmationAttempt('',false,()=>id))
})
test('confirmation normalizes and freezes exact request',()=>{
  const attempt=createConfirmationAttempt(' Checked ',true,()=>id)
  assert.deepEqual(attempt,{requestId:id,programmeComplete:true,note:'Checked'})
  assert.equal(Object.isFrozen(attempt),true)
})
test('confirmation enforces note length',()=>assert.throws(()=>createConfirmationAttempt('x'.repeat(501),true,()=>id)))
test('confirmation rejects invalid UUID',()=>assert.throws(()=>createConfirmationAttempt('',true,()=> 'invalid')))
test('ID creation failure is an error, not a submitted request',()=>assert.throws(()=>createConfirmationAttempt('',true,()=>{throw new Error('unavailable')})))
test('revocation requires reason',()=>assert.throws(()=>createRevocationAttempt({id,revision:1},' ')))
test('revocation binds original ID and revision',()=>{
  const source={id,revision:2};const attempt=createRevocationAttempt(source,' Correction ')
  source.id='different';source.revision=3
  assert.deepEqual(attempt,{id,revision:2,reason:'Correction'});assert.equal(Object.isFrozen(attempt),true)
})
test('revocation enforces reason length',()=>assert.throws(()=>createRevocationAttempt({id,revision:1},'x'.repeat(1001))))
test('empty and unpublished programmes are not ready',()=>{
  assert.equal(unitsReady(null),false);assert.equal(unitsReady([]),false)
  assert.equal(unitsReady([{status:'PUBLISHED'},{status:'DRAFT'}]),false)
  assert.equal(unitsReady([{status:'PUBLISHED'}]),true)
})
test('new confirmation requires all gating conditions',()=>{
  assert.equal(canConfirm(flags()),true)
  for(const key of ['permission','finalKnown','unitsReady','complete']) assert.equal(canConfirm({...flags(),[key]:false}),false)
  for(const key of ['loading','busy','otherPending','active']) assert.equal(canConfirm({...flags(),[key]:true}),false)
})
test('uncertain replay is still blocked without permission or known status',()=>{
  assert.equal(canConfirm({...flags(),pending:true,complete:false,unitsReady:false}),true)
  assert.equal(canConfirm({...flags(),pending:true,permission:false}),false)
  assert.equal(canConfirm({...flags(),pending:true,finalKnown:false}),false)
})
test('category filtering preserves server places',()=>{
  const rows=[{categoryCode:'A',overallPlace:2,categoryPlace:1},{categoryCode:'B',overallPlace:1,categoryPlace:1}]
  assert.deepEqual(filterCategory(rows,'A'),[rows[0]]);assert.equal(rows.length,2)
})
test('category choices use category codes and include legacy',()=>{
  const choices=categoryOptions([{categoryCode:'A',seedingGroupCode:'OPEN'},{categoryCode:'B',seedingGroupCode:'OPEN'},{}])
  assert.equal(choices.includes('A'),true);assert.equal(choices.includes('B'),true);assert.equal(choices.includes(LEGACY_CATEGORY),true)
  assert.equal(choices.includes('OPEN'),false)
})
test('all-category view preserves order',()=>{
  const rows=[{categoryCode:'B'},{categoryCode:'A'}];assert.deepEqual(filterCategory(rows,ALL_CATEGORIES),rows)
})
test('permission and finalized errors have workflow messages',()=>{
  assert.match(protocolErrorMessage({status:403}),/чужому событию/)
  assert.match(protocolErrorMessage({code:'results.discipline_finalized'}),/снятия финализации/)
})
