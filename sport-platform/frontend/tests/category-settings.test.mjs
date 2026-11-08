import test from 'node:test'
import assert from 'node:assert/strict'
import { parseCategoryJson, presetCategories } from '../src/features/regulations/category-settings.mjs'

test('legacy mode omits categories', () => assert.deepEqual(presetCategories('NONE'), []))
test('mixed heats preserve independent scoring categories', () => {
  const categories = presetCategories('SEX_MIXED')
  assert.deepEqual(categories.map(c => c.code), ['MEN', 'WOMEN'])
  assert.deepEqual(categories.map(c => c.seedingGroupCode), ['OPEN', 'OPEN'])
  assert.deepEqual(categories.map(c => c.eligibility.allowedGenders), [['MALE'], ['FEMALE']])
})
test('separate heats have different seeding groups', () => {
  assert.deepEqual(presetCategories('SEX_SEPARATE').map(c => c.seedingGroupCode), ['MEN', 'WOMEN'])
})
test('preserves existing age rules and category ordering', () => {
  const categories = [{ code: 'JUNIOR', seedingGroupCode: 'OPEN', eligibility: {
    ageMethod: 'BY_YEAR', ageReferenceDate: '2026-12-31', minAge: 12, maxAge: 17, allowedGenders: ['FEMALE'],
  } }]
  assert.deepEqual(parseCategoryJson(JSON.stringify(categories)), categories)
})
test('duplicate category codes rejected', () => {
  const c = presetCategories('SEX_MIXED')
  c[1].code = c[0].code
  assert.throws(() => parseCategoryJson(JSON.stringify(c)))
})
test('bad identifiers, missing eligibility and unknown fields rejected', () => {
  for (const c of [
    { code: 'bad code', seedingGroupCode: 'G', eligibility: {} },
    { code: 'A', seedingGroupCode: 'G' },
    { code: 'A', seedingGroupCode: 'G', eligibility: {}, surprise: true },
  ]) assert.throws(() => parseCategoryJson(JSON.stringify([c])))
})
test('bad genders rejected', () => {
  for (const allowedGenders of [[], ['OTHER'], ['MALE', 'MALE']]) {
    assert.throws(() => parseCategoryJson(JSON.stringify([
      { code: 'A', seedingGroupCode: 'G', eligibility: { allowedGenders } },
    ])))
  }
})
test('incomplete age configuration rejected', () => {
  assert.throws(() => parseCategoryJson(JSON.stringify([
    { code: 'A', seedingGroupCode: 'G', eligibility: { minAge: 12 } },
  ])))
})
test('reversed age bounds rejected', () => {
  assert.throws(() => parseCategoryJson(JSON.stringify([
    { code: 'A', seedingGroupCode: 'G', eligibility: {
      ageMethod: 'ON_DATE', ageReferenceDate: '2026-01-01', minAge: 18, maxAge: 12,
    } },
  ])))
})
test('empty custom array and broken JSON rejected', () => {
  assert.throws(() => parseCategoryJson('[]'))
  assert.throws(() => parseCategoryJson('{'))
})
