import test from 'node:test'
import assert from 'node:assert/strict'
import {
  competitionFormatLabel,
  competitionUnitTypeLabel,
  resultTypeLabel,
} from '../src/lib/discipline-labels.mjs'

test('known competition formats have Russian labels', () => {
  assert.equal(competitionFormatLabel('INDIVIDUAL'), 'индивидуальный')
  assert.equal(competitionFormatLabel('TEAM'), 'командный')
})

test('heat and result types have Russian labels', () => {
  assert.equal(competitionUnitTypeLabel('HEAT'), 'заплыв')
  assert.equal(resultTypeLabel('TIME'), 'время')
  assert.equal(resultTypeLabel('POINTS'), 'очки')
})

test('missing values have an explicit placeholder', () => {
  for (const format of [
    competitionFormatLabel, competitionUnitTypeLabel, resultTypeLabel,
  ]) {
    for (const value of [null, undefined, '']) {
      assert.equal(format(value), '—')
    }
  }
})

test('unknown values are not classified as known types', () => {
  assert.equal(
    competitionFormatLabel('FUTURE'),
    'Неизвестное значение: FUTURE',
  )
  assert.equal(
    competitionUnitTypeLabel('FUTURE'),
    'Неизвестное значение: FUTURE',
  )
  assert.equal(resultTypeLabel('FUTURE'), 'Неизвестное значение: FUTURE')
})

test('prototype properties are not accepted as labels', () => {
  for (const format of [
    competitionFormatLabel, competitionUnitTypeLabel, resultTypeLabel,
  ]) {
    for (const value of ['constructor', 'toString', '__proto__']) {
      assert.equal(format(value), `Неизвестное значение: ${value}`)
    }
  }
})

test('codes are not silently trimmed or normalized', () => {
  assert.equal(
    competitionFormatLabel('team'),
    'Неизвестное значение: team',
  )
  assert.equal(
    competitionUnitTypeLabel(' HEAT '),
    'Неизвестное значение:  HEAT ',
  )
})

test('invalid non-string values are explicit', () => {
  for (const format of [
    competitionFormatLabel, competitionUnitTypeLabel, resultTypeLabel,
  ]) {
    for (const value of [0, false, {}, []]) {
      assert.equal(format(value), 'Некорректное значение')
    }
  }
})
