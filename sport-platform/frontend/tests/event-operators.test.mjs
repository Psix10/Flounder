import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeOperatorEmail, validateOperatorMembers, validateOperatorCandidates, changeAndReadOperators } from '../src/lib/event-operators.mjs'
const eventId='11111111-1111-4111-8111-111111111111'
const operatorId='22222222-2222-4222-8222-222222222222'
const otherId='33333333-3333-4333-8333-333333333333'
function member(changes={}) { return {id:otherId,eventId,operatorUserId:operatorId,assignedByUserId:otherId,createdAt:'2026-11-15T19:00:00Z',email:'operator@example.com',displayName:'Оператор Иван',eligible:true,...changes} }
function candidate(changes={}) { return {userId:operatorId,email:'operator@example.com',displayName:'Оператор Иван',assigned:false,...changes} }
test('email is trimmed without changing displayed case',()=>assert.equal(normalizeOperatorEmail(' Operator@example.com '),'Operator@example.com'))
test('invalid emails are rejected before requests',()=>{for(const email of ['', 'a', 'a@@b', 'a b@example.com', 'a'+ 'x'.repeat(254)+'@b']) assert.throws(()=>normalizeOperatorEmail(email))})
test('email plus aliases are kept exact',()=>assert.equal(normalizeOperatorEmail('a+judge@example.com'),'a+judge@example.com'))
test('an empty assignment list is valid',()=>assert.deepEqual(validateOperatorMembers([],eventId),[]))
test('member data is preserved',()=>{const row=member();assert.equal(validateOperatorMembers([row],eventId)[0],row)})
test('assignment for another event is rejected',()=>assert.throws(()=>validateOperatorMembers([member({eventId:otherId})],eventId)))
test('missing read body is rejected',()=>assert.throws(()=>validateOperatorMembers(undefined,eventId)))
test('duplicate operator assignments are rejected',()=>assert.throws(()=>validateOperatorMembers([member(),member({id:eventId})],eventId)))
test('malformed member data is rejected',()=>assert.throws(()=>validateOperatorMembers([member({eligible:'yes'})],eventId)))
test('inactive assigned operator remains visible',()=>assert.equal(validateOperatorMembers([member({eligible:false})],eventId)[0].eligible,false))
test('deleted user representation remains removable by user id',()=>assert.equal(validateOperatorMembers([member({email:null,displayName:'Пользователь недоступен',eligible:false})],eventId)[0].operatorUserId,operatorId))
test('empty candidate result is valid',()=>assert.deepEqual(validateOperatorCandidates([],'operator@example.com'),[]))
test('candidate email matching is case insensitive',()=>assert.equal(validateOperatorCandidates([candidate()],'Operator@example.com').length,1))
test('candidate for a different email is rejected',()=>assert.throws(()=>validateOperatorCandidates([candidate({email:'other@example.com'})],'operator@example.com')))
test('ambiguous candidates are rejected',()=>assert.throws(()=>validateOperatorCandidates([candidate(),candidate({userId:otherId})],'operator@example.com')))
test('void assign response is followed by one read',async()=>{
  const calls=[]
  const rows=await changeAndReadOperators(async()=>{calls.push('PUT')},async()=>{calls.push('GET');return [member()]},eventId,operatorId,true)
  assert.equal(rows.length,1);assert.deepEqual(calls,['PUT','GET'])
})
test('void delete response is followed by one read',async()=>{
  const calls=[]
  const rows=await changeAndReadOperators(async()=>{calls.push('DELETE')},async()=>{calls.push('GET');return []},eventId,operatorId,false)
  assert.deepEqual(rows,[]);assert.deepEqual(calls,['DELETE','GET'])
})
test('write error is not retried',async()=>{
  let writes=0,reads=0
  await assert.rejects(changeAndReadOperators(async()=>{writes++;throw Error('403')},async()=>{reads++;return []},eventId,operatorId,true),/403/)
  assert.equal(writes,1);assert.equal(reads,0)
})
test('failed read after successful write does not repeat mutation',async()=>{
  let writes=0
  await assert.rejects(changeAndReadOperators(async()=>{writes++},async()=>{throw Error('offline')},eventId,operatorId,true),/offline/)
  assert.equal(writes,1)
})
test('concurrent removal after assign requires explicit synchronization',async()=>{
  await assert.rejects(changeAndReadOperators(async()=>undefined,async()=>[],eventId,operatorId,true),/отличается/)
})
test('unexpected persistence after remove is not reported as success',async()=>{
  await assert.rejects(changeAndReadOperators(async()=>undefined,async()=>[member()],eventId,operatorId,false),/отличается/)
})
test('another event response after mutation is rejected',async()=>{
  await assert.rejects(changeAndReadOperators(async()=>undefined,async()=>[member({eventId:otherId})],eventId,operatorId,true))
})
