import test from 'node:test'
import assert from 'node:assert/strict'
import { parseDisciplineId } from '../src/lib/discipline-id.mjs'

const id = 'f4a45e2e-3d99-46c4-aaab-4253ad4b4f3d'

test('valid discipline UUID is preserved', () => {
  assert.deepEqual(parseDisciplineId(id), {
    disciplineId: id, errorMessage: null,
  })
})

test('uppercase UUID is accepted without changing case', () => {
  assert.deepEqual(parseDisciplineId(id.toUpperCase()), {
    disciplineId: id.toUpperCase(), errorMessage: null,
  })
})

test('surrounding whitespace is trimmed', () => {
  assert.deepEqual(parseDisciplineId(`  ${id}\n`), {
    disciplineId: id, errorMessage: null,
  })
})

test('empty input has a required-field message', () => {
  for (const value of ['', ' \n ']) {
    assert.deepEqual(parseDisciplineId(value), {
      disciplineId: '', errorMessage: 'Введите ID дисциплины.',
    })
  }
})

test('placeholder and malformed IDs cannot become a request ID', () => {
  for (const value of [
    'ID_ДИСЦИПЛИНЫ',
    'undefined',
    'null',
    id.replaceAll('-', ''),
    `${id}x`,
    id.slice(1),
    id.replace('f', 'g'),
    `${id}?eventId=test`,
    id.replace('-', ' '),
  ]) {
    const result = parseDisciplineId(value)
    assert.equal(result.disciplineId, '')
    assert.match(result.errorMessage, /Некорректный ID дисциплины/)
  }
})

test('non-string values do not become identifiers', () => {
  for (const value of [null, undefined, 42, {}, [id]]) {
    const result = parseDisciplineId(value)
    assert.equal(result.disciplineId, '')
    assert.equal(typeof result.errorMessage, 'string')
  }
})

test('UUID shape does not impose an extra version restriction', () => {
  const value = '00000000-0000-0000-0000-000000000000'
  assert.deepEqual(parseDisciplineId(value), {
    disciplineId: value, errorMessage: null,
  })
})
