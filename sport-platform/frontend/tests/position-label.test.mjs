import test from 'node:test'
import assert from 'node:assert/strict'
import { positionLabel } from '../src/lib/position-label.mjs'

test('assigned positions display the exact number including zero', () => {
  for (const value of [0, 1, 8, 10]) {
    assert.equal(positionLabel(value), `Дорожка / позиция ${value}`)
  }
})

test('null and omitted positions have an explicit unassigned label', () => {
  assert.equal(positionLabel(null), 'Дорожка / позиция не назначена')
  assert.equal(positionLabel(undefined), 'Дорожка / позиция не назначена')
  assert.equal(positionLabel({}.laneOrPosition), 'Дорожка / позиция не назначена')
})

test('invalid positions are not silently converted or presented as assigned', () => {
  for (const value of [
    '', '3', false, true, {}, [], -1, 1.5, NaN, Infinity,
    Number.MAX_SAFE_INTEGER + 1,
  ]) {
    assert.equal(positionLabel(value), 'Некорректный номер дорожки / позиции')
  }
})
