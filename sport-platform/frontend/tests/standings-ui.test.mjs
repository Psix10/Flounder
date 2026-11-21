import test from 'node:test'
import assert from 'node:assert/strict'
import {readApiFailure,failureMessage,isMissingFinal,keepAttemptAfterFailure,categoryOptions,filterStandings,formatTimestamp} from '../src/lib/standings-ui.mjs'
test('HTTP error preserves structured violations',()=>{
  assert.deepEqual(readApiFailure({code:'protocol.category_violation',message:'Invalid',violations:[{field:'registrations[id].category',message:'NO_MATCHING_CATEGORY'}]},400),
    {code:'protocol.category_violation',message:'Invalid',violations:[{field:'registrations[id].category',message:'NO_MATCHING_CATEGORY'}]})
})
test('malformed violations are ignored safely',()=>{
  assert.deepEqual(readApiFailure({violations:[null,{},'bad',{field:'a',message:1},{field:'b',message:'valid',secret:'discard'}]},400).violations,[{field:'b',message:'valid'}])
})
test('non JSON errors have HTTP fallback',()=>{
  assert.equal(readApiFailure(null,502).message,'Не удалось выполнить запрос. Код ответа: 502.')
  assert.equal(readApiFailure('gateway error',502).code,undefined)
})
test('only typed final-not-found is treated as absent snapshot',()=>{
  assert.equal(isMissingFinal({status:404,code:'results.final_not_found'}),true)
  assert.equal(isMissingFinal({status:404,code:'request_error'}),false)
  assert.equal(isMissingFinal({status:404}),false)
})
test('network failure is not absence of finalization',()=>assert.equal(isMissingFinal(new Error('network')),false))
test('network ambiguity retains the same confirmation attempt',()=>assert.equal(keepAttemptAfterFailure(new Error('network')),true))
test('server failure retains the same confirmation attempt',()=>assert.equal(keepAttemptAfterFailure({status:500}),true))
test('timeouts and rate limits retain the same attempt',()=>{
  assert.equal(keepAttemptAfterFailure({status:408}),true)
  assert.equal(keepAttemptAfterFailure({status:429}),true)
})
test('definitive client rejections permit correcting the form',()=>{
  for(const status of [400,401,403,404,409,422]) assert.equal(keepAttemptAfterFailure({status}),false)
})
test('frozen discipline error explains the revocation workflow',()=>{
  assert.match(failureMessage({code:'results.discipline_finalized'}),/снять финализацию/)
})
test('permission failure does not imply that a role owns every event',()=>assert.match(failureMessage({status:403}),/не даёт доступ ко всем/))
test('authentication failure requests a new login',()=>assert.match(failureMessage({status:401}),/Войдите/))
test('unknown error code retains server message',()=>assert.equal(failureMessage({code:'new.code',message:'Specific'}),'Specific'))
test('prototype names are not mistaken for error translations',()=>assert.equal(failureMessage({code:'constructor',message:'Safe'}),'Safe'))
test('category choices are distinct codes, not seeding groups',()=>{
  assert.deepEqual(categoryOptions([{categoryCode:'B',seedingGroupCode:'OPEN'},{categoryCode:'A',seedingGroupCode:'OPEN'},{categoryCode:'A'},{categoryCode:null}]),['A','B'])
})
test('category filter keeps server category and overall places',()=>{
  const entries=[{categoryCode:'A',categoryPlace:1,overallPlace:2},{categoryCode:'B',categoryPlace:1,overallPlace:1},{categoryCode:'A',categoryPlace:2,overallPlace:3}]
  assert.deepEqual(filterStandings(entries,'A'),[entries[0],entries[2]])
  assert.equal(entries.length,3)
  assert.equal(filterStandings(entries,'A')[0].overallPlace,2)
})
test('legacy filter includes both null and omitted category',()=>assert.equal(filterStandings([{categoryCode:null},{},{categoryCode:'A'}],'__legacy__').length,2))
test('all-category view preserves order and returns a separate array',()=>{
  const entries=[{categoryCode:'B'},{categoryCode:'A'}];const filtered=filterStandings(entries,'')
  assert.deepEqual(filtered,entries);assert.notEqual(filtered,entries)
})
test('invalid dates render a placeholder',()=>{
  assert.equal(formatTimestamp('invalid'),'—');assert.equal(formatTimestamp(null),'—')
})
test('valid dates can be rendered without throwing',()=>assert.notEqual(formatTimestamp('2026-11-03T16:00:00Z'),'—'))
