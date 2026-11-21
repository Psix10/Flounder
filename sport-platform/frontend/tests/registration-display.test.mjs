import test from 'node:test'
import assert from 'node:assert/strict'
import {
  registrationEventTitle, registrationDisciplineName,
  registrationParticipantName, mergeRegistrationDisplay,
} from '../src/lib/registration-display.mjs'

const original = () => ({
  id: 'registration-1', eventId: 'event-1', eventDisciplineId: 'discipline-1',
  eventTitle: 'Кубок', disciplineName: '50 м вольный стиль',
  status: 'NEEDS_CORRECTION',
})

test('event and discipline names replace technical identifiers', () => {
  assert.equal(registrationEventTitle(original()), 'Кубок')
  assert.equal(registrationDisciplineName(original()), '50 м вольный стиль')
})

test('missing labels do not expose identifiers as names', () => {
  for (const value of [null, undefined, {}, {
    eventId: 'event-secret', eventDisciplineId: 'discipline-secret',
    eventTitle: ' ', disciplineName: '',
  }]) {
    assert.equal(registrationEventTitle(value), 'Название мероприятия недоступно')
    assert.equal(registrationDisciplineName(value), 'Название дисциплины недоступно')
  }
})

test('participant name uses the registration snapshot', () => {
  assert.equal(registrationParticipantName({
    participantSnapshot: {
      lastName: ' Иванов ', firstName: ' Иван ', middleName: null,
    },
  }), 'Иванов Иван')
  assert.equal(registrationParticipantName({}), 'ФИО не указано')
})

test('mutation response preserves labels for exactly the same scope', () => {
  const current = original()
  const updated = {
    ...current, eventTitle: null, disciplineName: null,
    status: 'SUBMITTED', participantSnapshot: { firstName: 'Новое имя' },
  }
  const result = mergeRegistrationDisplay(current, updated)
  assert.equal(result.eventTitle, 'Кубок')
  assert.equal(result.disciplineName, '50 м вольный стиль')
  assert.equal(result.status, 'SUBMITTED')
  assert.deepEqual(result.participantSnapshot, updated.participantSnapshot)
  assert.equal(updated.eventTitle, null)
  assert.equal(current.status, 'NEEDS_CORRECTION')
})

test('labels are not carried across changed registration or scope', () => {
  for (const key of ['id', 'eventId', 'eventDisciplineId']) {
    const updated = {
      ...original(), [key]: 'other', eventTitle: null, disciplineName: null,
    }
    assert.deepEqual(mergeRegistrationDisplay(original(), updated), updated)
  }
})

test('fresh server labels override earlier labels', () => {
  const updated = {
    ...original(), eventTitle: 'Новый кубок', disciplineName: '100 м',
  }
  assert.deepEqual(mergeRegistrationDisplay(original(), updated), updated)
})
