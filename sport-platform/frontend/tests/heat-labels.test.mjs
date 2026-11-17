import test from 'node:test'
import assert from 'node:assert/strict'
import { formatHeatLabel } from '../src/lib/heat-labels.mjs'
test('legacy unit heading',()=>assert.equal(formatHeatLabel('Заход 1'),'Заплыв 1'))
test('discipline heading',()=>assert.equal(formatHeatLabel('100 м — по заходам'),'100 м — по заплывам'))
test('all lowercase grammatical forms',()=>assert.equal(
  formatHeatLabel('заход захода заходу заходом заходе заходы заходов заходам заходами заходах'),
  'заплыв заплыва заплыву заплывом заплыве заплывы заплывов заплывам заплывами заплывах'))
test('uppercase preserved',()=>assert.equal(formatHeatLabel('ЗАХОДОВ: 2'),'ЗАПЛЫВОВ: 2'))
test('already renamed values unchanged',()=>assert.equal(formatHeatLabel('Заплыв 2'),'Заплыв 2'))
test('does not change parts of words or identifiers',()=>assert.equal(formatHeatLabel('перезаход заходить Заход_ID'),'перезаход заходить Заход_ID'))
test('other sports and HEAT code unchanged',()=>assert.equal(formatHeatLabel('Матч 1 / Забег 2 / HEAT'),'Матч 1 / Забег 2 / HEAT'))
test('punctuation preserved',()=>assert.equal(formatHeatLabel('«Заход 1», заход №2.'),'«Заплыв 1», заплыв №2.'))
test('null and undefined have a display fallback',()=>{assert.equal(formatHeatLabel(null),'—');assert.equal(formatHeatLabel(undefined),'—')})
test('empty label stays empty',()=>assert.equal(formatHeatLabel(''),''))
test('historical snapshot is not mutated',()=>{
  const snapshot=Object.freeze({revision:2,unitLabel:'Заход 1',rawValue:'01:04.00'})
  assert.equal(formatHeatLabel(snapshot.unitLabel),'Заплыв 1')
  assert.equal(snapshot.unitLabel,'Заход 1');assert.equal(snapshot.rawValue,'01:04.00')
})
test('formatting is idempotent',()=>{const text='Заход 1 — по заходам';assert.equal(formatHeatLabel(formatHeatLabel(text)),formatHeatLabel(text))})
