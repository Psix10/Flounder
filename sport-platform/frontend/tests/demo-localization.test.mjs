import test from 'node:test'
import assert from 'node:assert/strict'
import { readApiFailure } from '../src/lib/standings-ui.mjs'
import {
  inspectCategories, configuredGenderWarning, previewCategory,
} from '../src/lib/protocol-inspection.mjs'
import { presetCategories } from '../src/features/regulations/category-settings.mjs'

test('missing response message has a Russian fallback', () => {
  for (const payload of [undefined, null, {}, 'not-json']) {
    assert.equal(
      readApiFailure(payload, 503).message,
      'Не удалось выполнить запрос. Код ответа: 503.',
    )
  }
})

test('API parser preserves business message, code and valid violations', () => {
  assert.deepEqual(readApiFailure({
    message: 'Проверьте дату рождения.',
    code: 'registrations.invalid_snapshot',
    violations: [
      { field: 'birthDate', message: 'Некорректная дата.' },
      null,
      { field: 7, message: 'invalid' },
    ],
  }, 400), {
    message: 'Проверьте дату рождения.',
    code: 'registrations.invalid_snapshot',
    violations: [{ field: 'birthDate', message: 'Некорректная дата.' }],
  })
})

test('category presets preserve contract codes and grouping', () => {
  for (const mode of ['SEX_MIXED', 'SEX_SEPARATE']) {
    const categories = presetCategories(mode)
    assert.deepEqual(categories.map(item => item.code), ['MEN', 'WOMEN'])
    assert.deepEqual(
      categories.map(item => item.seedingGroupCode),
      mode === 'SEX_MIXED' ? ['OPEN', 'OPEN'] : ['MEN', 'WOMEN'],
    )
    assert.deepEqual(
      categories.map(item => item.eligibility.allowedGenders),
      [['MALE'], ['FEMALE']],
    )
  }
})

test('year-based age description is localized without changing classification', () => {
  const inspection = inspectCategories({
    categories: [{
      code: 'JUNIOR_CUSTOM',
      seedingGroupCode: 'GROUP_CUSTOM',
      eligibility: {
        ageMethod: 'BY_YEAR',
        ageReferenceDate: '2026-12-31',
        minAge: 10,
        maxAge: 12,
        allowedGenders: ['MALE'],
      },
    }],
  })
  assert.equal(inspection.state, 'ready')
  assert.match(inspection.categories[0].description, /возраст по году рождения/)
  assert.doesNotMatch(inspection.categories[0].description, /BY_YEAR/)
  assert.match(inspection.categories[0].description, /2014–2016/)
  const preview = previewCategory(
    { birthDate: '2015-06-01', gender: 'MALE' }, inspection,
  )
  assert.equal(preview.state, 'matched')
  assert.equal(preview.code, 'JUNIOR_CUSTOM')
  assert.equal(preview.seedingGroupCode, 'GROUP_CUSTOM')
})

test('missing eligibility has a localized diagnostic', () => {
  const inspection = inspectCategories({
    categories: [{ code: 'A', seedingGroupCode: 'A' }],
  })
  assert.equal(inspection.state, 'invalid')
  assert.equal(inspection.message, 'У категории отсутствуют настройки допуска.')
})

test('unsupported eligibility does not become a valid category', () => {
  const inspection = inspectCategories({
    categories: [{
      code: 'A',
      seedingGroupCode: 'A',
      eligibility: { unsupported: true },
    }],
  })
  assert.equal(inspection.state, 'invalid')
  assert.match(inspection.message, /Неизвестные параметры допуска/)
})

test('gender mismatch warning is localized and matching gender stays valid', () => {
  assert.equal(
    configuredGenderWarning({ gender: 'MALE' }, { gender: 'FEMALE' }),
    'Пол, сохранённый в заявке, не соответствует ограничению дисциплины. Требуется проверка допуска на сервере.',
  )
  assert.equal(
    configuredGenderWarning({ gender: 'MALE' }, { gender: 'MALE' }),
    null,
  )
})
