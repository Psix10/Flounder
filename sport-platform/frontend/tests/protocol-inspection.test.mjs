import test from 'node:test'
import assert from 'node:assert/strict'
import { inspectCategories, previewCategory, disciplineFacts, snapshotYear, snapshotGender,
  parseCalendarDate, configuredGenderWarning, savedCategoryLabel } from '../src/lib/protocol-inspection.mjs'
const rule=(code, sex, minAge=10, maxAge=12, extra={})=>({code,seedingGroupCode:code,eligibility:{allowedGenders:[sex],ageMethod:'BY_YEAR',ageReferenceDate:'2026-11-16',minAge,maxAge,...extra}})
const settings=(categories)=>({categories})
const snapshot=(birthDate='2014-12-31',gender='MALE')=>({birthDate,gender})
const inspect=(categories)=>inspectCategories(settings(categories))
const one=()=>inspect([rule('M_2014_2016','MALE')])
test('null settings are unavailable, not proof of absent categories',()=>assert.equal(inspectCategories(null).state,'unavailable'))
test('absent categories mean legacy grouping',()=>assert.equal(inspectCategories({capacity:4}).state,'disabled'))
test('empty categories returned by configuration read mean disabled',()=>assert.equal(inspect([]).state,'disabled'))
test('BY_YEAR conditions display birth year intervals',()=>assert.match(one().categories[0].description,/2014–2016 г\. р\./))
test('2014 December birthday is included by year',()=>assert.equal(previewCategory(snapshot(),one()).state,'matched'))
test('2016 January birthday is included by year',()=>assert.equal(previewCategory(snapshot('2016-01-01'),one()).state,'matched'))
test('2013 is outside 2014-2016',()=>assert.equal(previewCategory(snapshot('2013-12-31'),one()).state,'no-match'))
test('2017 is outside 2014-2016',()=>assert.equal(previewCategory(snapshot('2017-01-01'),one()).state,'no-match'))
test('female does not match male-only category',()=>assert.equal(previewCategory(snapshot('2014-01-01','FEMALE'),one()).state,'no-match'))
test('snapshot gender is case insensitive without trimming',()=>assert.equal(previewCategory(snapshot('2014-01-01','male'),one()).state,'matched'))
test('gender with whitespace is not silently normalized',()=>assert.equal(previewCategory(snapshot('2014-01-01',' MALE '),one()).state,'no-match'))
test('missing birth date is explicit',()=>assert.match(previewCategory({gender:'MALE'},one()).text,/Не указана дата рождения/))
test('future birth date cannot match BY_YEAR',()=>{
  const rules=inspect([rule('BABY','MALE',0,0)])
  assert.equal(previewCategory(snapshot('2026-12-01'),rules).state,'no-match')
})
test('ON_DATE uses full years before birthday',()=>{
  const rules=inspect([rule('M','MALE',12,12,{ageMethod:'ON_DATE'})])
  assert.equal(previewCategory(snapshot('2014-11-17'),rules).state,'no-match')
  assert.equal(previewCategory(snapshot('2014-11-16'),rules).state,'matched')
})
test('overlapping categories are not resolved arbitrarily',()=>{
  const rules=inspect([rule('A','MALE'),rule('B','MALE')])
  assert.equal(previewCategory(snapshot(),rules).state,'ambiguous')
})
test('invalid second category prevents misleading partial preview',()=>assert.equal(inspect([rule('A','MALE'),{code:'B'}]).state,'invalid'))
test('unknown policy field is not ignored',()=>assert.equal(inspect([rule('A','MALE',10,12,{minBirthYear:2014})]).state,'invalid'))
test('duplicate category codes are rejected',()=>assert.equal(inspect([rule('A','MALE'),rule('A','FEMALE')]).state,'invalid'))
test('shared seeding group does not erase category identity',()=>{
  const a=rule('M','MALE'),b=rule('F','FEMALE');a.seedingGroupCode='OPEN';b.seedingGroupCode='OPEN'
  const result=previewCategory(snapshot(),inspect([a,b]));assert.equal(result.code,'M');assert.equal(result.seedingGroupCode,'OPEN')
})
test('empty eligibility has no snapshot requirements, like disabled server policy',()=>{
  const rules=inspect([{code:'ALL',seedingGroupCode:'OPEN',eligibility:{}}])
  assert.equal(previewCategory(null,rules).state,'matched')
})
test('calendar dates are validated rather than rolled into next month',()=>{
  assert.equal(parseCalendarDate('2015-02-29'),null);assert.notEqual(parseCalendarDate('2016-02-29'),null)
})
test('snapshot birth year does not depend on browser timezone',()=>assert.equal(snapshotYear(snapshot('2014-01-01')),'2014'))
test('snapshot gender display preserves missing distinction',()=>assert.equal(snapshotGender({gender:null}),'Не указан'))
test('structured discipline fields display without reading name',()=>assert.deepEqual(disciplineFacts({distanceMeters:50,stroke:'FREESTYLE',gender:'MALE',poolLengthMeters:25}),{error:null,distance:'50 м',stroke:'Вольный стиль',gender:'Мужской',pool:'25 м'}))
test('missing metadata is not inferred from a discipline name',()=>assert.equal(disciplineFacts({name:'50 м брасс'}).distance,'Не задано'))
test('invalid JSON settings are explicit',()=>assert.ok(disciplineFacts('{').error))
test('unknown stroke remains visible as raw code',()=>assert.equal(disciplineFacts({stroke:'FUTURE'}).stroke,'Код: FUTURE'))
test('negative distance is not presented as valid',()=>assert.equal(disciplineFacts({distanceMeters:-50}).distance,'Некорректное значение'))
test('gender mismatch with discipline is a separate warning',()=>assert.match(configuredGenderWarning({gender:'MALE'},snapshot('2014-01-01','FEMALE')),/не совпадает/))
test('missing discipline restriction is not guessed',()=>assert.equal(configuredGenderWarning({},snapshot()),null))
test('saved unknown category code is not replaced by current preview',()=>assert.match(savedCategoryLabel('HISTORICAL',one()),/Описание кода отсутствует/))
test('saved null category stays explicitly absent',()=>assert.equal(savedCategoryLabel(null,one()),'Категория не сохранена'))
test('inspection and preview do not mutate input snapshots or rules',()=>{
  const value=settings([rule('A','MALE')]),person=snapshot();const before=JSON.stringify({value,person})
  previewCategory(person,inspectCategories(value));assert.equal(JSON.stringify({value,person}),before)
})
