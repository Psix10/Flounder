import test from 'node:test'
import assert from 'node:assert/strict'
import { readEventCategories } from '../src/lib/event-category-read.mjs'
import { startRead } from '../src/lib/read-lifecycle.mjs'

const settle = () => new Promise(resolve => setImmediate(resolve))
const formatError = (_error, fallback) => fallback
const configuration = (id = 'version-a', protocol = {
  categories: [{
    code: 'AGE_12_13',
    seedingGroupCode: 'AGE_12_13',
    eligibility: {
      ageMethod: 'ON_DATE',
      ageReferenceDate: '2026-11-19',
      minAge: 12,
      maxAge: 13,
    },
  }],
}) => ({ document: { id }, protocol })

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

function handlers(log) {
  return {
    success: value => log.push(['success', value]),
    failure: error => log.push(['failure', error]),
    finished: () => log.push(['finished']),
  }
}

test('event categories: missing version performs no configuration request', async () => {
  let calls = 0
  for (const id of [undefined, null, '']) {
    const result = await readEventCategories(id, async () => {
      calls++
      return configuration()
    }, formatError)
    assert.equal(result.state, 'unavailable')
    assert.deepEqual(result.categories, [])
  }
  assert.equal(calls, 0)
})

test('event categories: reads the exact version once and describes configured age', async () => {
  const calls = []
  const result = await readEventCategories('version-a', async id => {
    calls.push(id)
    return configuration()
  }, formatError)
  assert.deepEqual(calls, ['version-a'])
  assert.equal(result.state, 'ready')
  assert.equal(result.categories[0].code, 'AGE_12_13')
  assert.match(result.categories[0].description, /12–13 полных лет на 2026-11-19/)
})

test('event categories: read failure is unavailable and is not retried', async () => {
  const failure = new Error('forbidden')
  let calls = 0
  const result = await readEventCategories('version-a', async () => {
    calls++
    throw failure
  }, (error, fallback) => {
    assert.equal(error, failure)
    assert.match(fallback, /Не удалось загрузить/)
    return 'Нет доступа к конфигурации.'
  })
  assert.equal(calls, 1)
  assert.equal(result.state, 'unavailable')
  assert.equal(result.message, 'Нет доступа к конфигурации.')
  assert.deepEqual(result.categories, [])
})

test('event categories: another version is never displayed', async () => {
  const result = await readEventCategories(
    'version-a', async () => configuration('version-b'), formatError,
  )
  assert.equal(result.state, 'unavailable')
  assert.match(result.message, /другой версии/)
  assert.deepEqual(result.categories, [])
})

test('event categories: missing response is unavailable', async () => {
  const result = await readEventCategories(
    'version-a', async () => undefined, formatError,
  )
  assert.equal(result.state, 'unavailable')
  assert.deepEqual(result.categories, [])
})

test('event categories: null protocol is unavailable, not disabled', async () => {
  const result = await readEventCategories(
    'version-a', async () => configuration('version-a', null), formatError,
  )
  assert.equal(result.state, 'unavailable')
})

test('event categories: configured empty categories are disabled', async () => {
  const result = await readEventCategories(
    'version-a',
    async () => configuration('version-a', { categories: [] }),
    formatError,
  )
  assert.equal(result.state, 'disabled')
})

test('event categories: malformed categories remain invalid', async () => {
  const result = await readEventCategories(
    'version-a',
    async () => configuration('version-a', { categories: [{}] }),
    formatError,
  )
  assert.equal(result.state, 'invalid')
  assert.deepEqual(result.categories, [])
})

test('event categories: cancelled success cannot update handlers', async () => {
  const request = deferred(), log = []
  const cancel = startRead(
    () => readEventCategories('version-a', () => request.promise, formatError),
    handlers(log),
  )
  await settle()
  cancel()
  request.resolve(configuration())
  await settle()
  assert.deepEqual(log, [])
})

test('event categories: cancelled read failure cannot update handlers', async () => {
  const request = deferred(), log = []
  const cancel = startRead(
    () => readEventCategories('version-a', () => request.promise, formatError),
    handlers(log),
  )
  await settle()
  cancel()
  request.reject(new Error('late network failure'))
  await settle()
  assert.deepEqual(log, [])
})

test('event categories: old version cannot replace the next read', async () => {
  const first = deferred(), log = []
  const cancel = startRead(
    () => readEventCategories('version-a', () => first.promise, formatError),
    handlers(log),
  )
  await settle()
  cancel()

  startRead(
    () => readEventCategories(
      'version-b', async () => configuration('version-b'), formatError,
    ),
    handlers(log),
  )
  await settle()
  first.resolve(configuration('version-a'))
  await settle()

  assert.equal(log.length, 2)
  assert.equal(log[0][0], 'success')
  assert.equal(log[0][1].state, 'ready')
  assert.deepEqual(log[1], ['finished'])
})

test('event categories: cleanup before start prevents configuration request', async () => {
  let calls = 0
  const log = []
  const cancel = startRead(
    () => readEventCategories('version-a', async () => {
      calls++
      return configuration()
    }, formatError),
    handlers(log),
  )
  cancel()
  await settle()
  assert.equal(calls, 0)
  assert.deepEqual(log, [])
})
