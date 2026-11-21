import test from 'node:test'
import assert from 'node:assert/strict'
import { startRead } from '../src/lib/read-lifecycle.mjs'

const settle = () => new Promise(resolve => setImmediate(resolve))
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function callbacks(log) {
  return {
    success: value => log.push(['success', value]),
    failure: error => log.push(['failure', error]),
    finished: () => log.push(['finished']),
  }
}

test('read runs once and returns data before finishing', async () => {
  const log = []
  let calls = 0
  startRead(async () => { calls++; return [] }, callbacks(log))
  await settle()
  assert.equal(calls, 1)
  assert.deepEqual(log, [['success', []], ['finished']])
})

test('failed read reports failure without retry', async () => {
  const log = [], error = new Error('forbidden')
  let calls = 0
  startRead(async () => { calls++; throw error }, callbacks(log))
  await settle()
  assert.equal(calls, 1)
  assert.deepEqual(log, [['failure', error], ['finished']])
})

test('cancelled pending success does not update state', async () => {
  const request = deferred(), log = []
  const cancel = startRead(() => request.promise, callbacks(log))
  await settle()
  cancel()
  request.resolve(['old'])
  await settle()
  assert.deepEqual(log, [])
})

test('cancelled pending failure does not update state', async () => {
  const request = deferred(), log = []
  const cancel = startRead(() => request.promise, callbacks(log))
  await settle()
  cancel()
  request.reject(new Error('late failure'))
  await settle()
  assert.deepEqual(log, [])
})

test('older response cannot replace the next read after cleanup', async () => {
  const first = deferred(), log = []
  const cancel = startRead(() => first.promise, callbacks(log))
  await settle()
  cancel()
  startRead(async () => ['new'], callbacks(log))
  await settle()
  first.resolve(['old'])
  await settle()
  assert.deepEqual(log, [['success', ['new']], ['finished']])
})

test('cleanup before start suppresses the obsolete read', async () => {
  let calls = 0
  const log = []
  const cancel = startRead(async () => { calls++; return [] }, callbacks(log))
  cancel()
  await settle()
  assert.equal(calls, 0)
  assert.deepEqual(log, [])
})
