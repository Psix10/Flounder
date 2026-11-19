import test from 'node:test'
import assert from 'node:assert/strict'
import {
  validateOperatorEvents, validateOperatorEvent, validateOperatorDisciplines, validateOperatorUnits,
  operatorEventPath, operatorDisciplinePath, operatorJudgePath, operatorResultsBackPath,
} from '../src/lib/operator-workspace.mjs'
const eventId='11111111-1111-4111-8111-111111111111'
const disciplineId='22222222-2222-4222-8222-222222222222'
const unitId='33333333-3333-4333-8333-333333333333'
const foreignId='44444444-4444-4444-8444-444444444444'
const event=(changes={})=>({id:eventId,title:'Демо',status:'DRAFT',eventStartAt:null,eventEndAt:null,publicSlug:null,...changes})
const discipline=(changes={})=>({id:disciplineId,eventId,name:'100 м',status:'PUBLISHED',competitionFormat:'INDIVIDUAL',unitType:'HEAT',resultType:'TIME',...changes})
const unit=(changes={})=>({id:unitId,eventDisciplineId:disciplineId,label:'Заход 1',status:'PUBLISHED',sequenceNumber:1,scheduledAt:null,...changes})
test('empty assignment list is valid',()=>assert.deepEqual(validateOperatorEvents([]),[]))
test('unpublished assigned event remains visible',()=>assert.equal(validateOperatorEvents([event()])[0].status,'DRAFT'))
test('unknown future event status is not silently hidden',()=>assert.equal(validateOperatorEvents([event({status:'FUTURE'})])[0].status,'FUTURE'))
test('duplicate event ids are rejected',()=>assert.throws(()=>validateOperatorEvents([event(),event()])))
test('missing events response is rejected',()=>assert.throws(()=>validateOperatorEvents(undefined)))
test('wrong event detail is rejected',()=>assert.throws(()=>validateOperatorEvent(event(),foreignId)))
test('event display data is preserved',()=>{const row=event();assert.equal(validateOperatorEvent(row,eventId),row)})
test('empty discipline list is valid',()=>assert.deepEqual(validateOperatorDisciplines([],eventId),[]))
test('foreign discipline response is rejected',()=>assert.throws(()=>validateOperatorDisciplines([discipline({eventId:foreignId})],eventId)))
test('draft discipline remains visible',()=>assert.equal(validateOperatorDisciplines([discipline({status:'DRAFT'})],eventId)[0].status,'DRAFT'))
test('duplicate discipline ids are rejected',()=>assert.throws(()=>validateOperatorDisciplines([discipline(),discipline()],eventId)))
test('empty unit list is valid',()=>assert.deepEqual(validateOperatorUnits([],disciplineId),[]))
test('unit from another discipline is rejected',()=>assert.throws(()=>validateOperatorUnits([unit({eventDisciplineId:foreignId})],disciplineId)))
test('unit validation does not rewrite historical labels',()=>{const row=unit();assert.equal(validateOperatorUnits([row],disciplineId)[0].label,'Заход 1')})
test('event navigation has no manual UUID input',()=>assert.equal(operatorEventPath(eventId),`/operator/events/${eventId}`))
test('discipline navigation preserves event context',()=>assert.equal(operatorDisciplinePath(eventId,disciplineId),`/operator/events/${eventId}/disciplines/${disciplineId}`))
test('judge navigation includes only event and discipline context',()=>{
  const url=new URL(operatorJudgePath(eventId,disciplineId,unitId),'https://example.invalid')
  assert.equal(url.pathname,`/operator/competition-units/${unitId}`)
  assert.deepEqual([...url.searchParams.keys()],['eventId','disciplineId'])
})
test('judge back link returns to the selected discipline',()=>assert.equal(operatorResultsBackPath(eventId,disciplineId,disciplineId,true),operatorDisciplinePath(eventId,disciplineId)))
test('mismatched judge context falls back to own event list',()=>assert.equal(operatorResultsBackPath(eventId,foreignId,disciplineId,true),'/operator/events'))
test('missing context for operator falls back to own event list',()=>assert.equal(operatorResultsBackPath(null,null,disciplineId,true),'/operator/events'))
test('organizer back route preserves matching event and discipline context',()=>{
  const url=new URL(operatorResultsBackPath(eventId,disciplineId,disciplineId,false),'https://example.invalid')
  assert.equal(url.pathname,'/operator/results')
  assert.deepEqual([...url.searchParams.entries()],[['eventId',eventId],['disciplineId',disciplineId]])
})
test('organizer missing context preserves legacy back route',()=>assert.equal(
  operatorResultsBackPath(null,null,disciplineId,false),'/operator/results'))
test('organizer mismatched discipline falls back to legacy back route',()=>assert.equal(
  operatorResultsBackPath(eventId,foreignId,disciplineId,false),'/operator/results'))
test('organizer invalid event context falls back to legacy back route',()=>assert.equal(
  operatorResultsBackPath('https://evil.example',disciplineId,disciplineId,false),'/operator/results'))
test('organizer unknown actual discipline falls back to legacy back route',()=>assert.equal(
  operatorResultsBackPath(eventId,disciplineId,undefined,false),'/operator/results'))
test('external redirects are not accepted as context',()=>assert.equal(operatorResultsBackPath('https://evil.example',disciplineId,disciplineId,true),'/operator/events'))
test('invalid ids do not generate navigation links',()=>assert.throws(()=>operatorEventPath('../admin')))
