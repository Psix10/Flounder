import test from 'node:test'
import assert from 'node:assert/strict'
import {
  registrationStatusLabel,
  canOfferWithdrawal,
  withdrawalReasonError,
  createWithdrawalPayload,
  withdrawalFailure,
  readWithdrawalHistory,
} from '../src/features/registrations/registration-withdrawal.mjs'

test('withdrawn label is distinct from legacy cancellation', () => {
  assert.equal(registrationStatusLabel('WITHDRAWN'), 'Снята с участия')
  assert.equal(registrationStatusLabel('CANCELLED'), 'Отменена')
})
test('existing labels remain available and unknown values are not misclassified', () => {
  assert.equal(registrationStatusLabel('CONFIRMED'), 'Подтверждена')
  assert.equal(registrationStatusLabel('NEEDS_CORRECTION'), 'Требует уточнения')
  assert.equal(registrationStatusLabel('FUTURE_STATUS'), 'FUTURE_STATUS')
  assert.equal(registrationStatusLabel('toString'), 'toString')
})
test('organizer and platform administrator may be offered the action', () => {
  assert.equal(canOfferWithdrawal('CONFIRMED', ['organizer']), true)
  assert.equal(canOfferWithdrawal('CONFIRMED', ['platform_admin']), true)
})
test('participant, operator and missing roles do not get the action', () => {
  for (const roles of [['operator'], ['participant'], [], ['ORGANIZER'], null]) {
    assert.equal(canOfferWithdrawal('CONFIRMED', roles), false)
  }
})
test('only confirmed status is offered withdrawal', () => {
  for (const status of ['SUBMITTED', 'NEEDS_CORRECTION', 'REJECTED', 'WITHDRAWN', 'CANCELLED']) {
    assert.equal(canOfferWithdrawal(status, ['organizer']), false)
  }
})
test('reason is required, including whitespace-only input', () => {
  for (const reason of ['', '   ', '\n\t', null, undefined, 42]) {
    assert.ok(withdrawalReasonError(reason))
  }
})
test('reason boundary matches the server length limit', () => {
  assert.equal(withdrawalReasonError('a'.repeat(2000)), null)
  assert.ok(withdrawalReasonError('a'.repeat(2001)))
  assert.ok(withdrawalReasonError(' ' + 'a'.repeat(2000)))
})
test('payload trims the reason and does not send financial state or profile edits', () => {
  assert.deepEqual(createWithdrawalPayload('  Тестовый участник  ', true), {
    reason: 'Тестовый участник',
  })
})
test('explicit confirmation is required', () => {
  for (const confirmed of [false, undefined, 'true', 1]) {
    assert.throws(() => createWithdrawalPayload('Reason', confirmed))
  }
})
test('payload cannot bypass blank or oversized reason validation', () => {
  assert.throws(() => createWithdrawalPayload(' ', true))
  assert.throws(() => createWithdrawalPayload('a'.repeat(2001), true))
})
test('unknown network result requires refresh before another request', () => {
  const result = withdrawalFailure(undefined)
  assert.equal(result.requiresRefresh, true)
  assert.match(result.message, /уже могла быть снята/)
})
test('conflict and permission errors require a refresh and retain server details', () => {
  for (const status of [401, 403, 404, 409, 500]) {
    const result = withdrawalFailure(status, 'Details from server')
    assert.equal(result.requiresRefresh, true)
    assert.match(result.message, /Details from server/)
  }
})
test('validation error allows correcting the form without automatic retry', () => {
  assert.equal(withdrawalFailure(400, 'Invalid reason').requiresRefresh, false)
})
test('valid withdrawal audit is retained without mutation', () => {
  const item = Object.freeze({
    operation: 'WITHDRAW_CONFIRMED_REGISTRATION', fromStatus: 'CONFIRMED',
    toStatus: 'WITHDRAWN', reason: 'Reason', actorUserId: 'actor', occurredAt: '2026-11-15T13:35:16Z',
  })
  const input = Object.freeze([item])
  assert.deepEqual(readWithdrawalHistory(input), [item])
  assert.equal(input.length, 1)
})
test('malformed history is ignored instead of crashing the page', () => {
  for (const input of [null, {}, 'not-an-array']) {
    assert.deepEqual(readWithdrawalHistory(input), [])
  }
  assert.deepEqual(readWithdrawalHistory([null, [], {}, { operation: 'OTHER' }]), [])
})
