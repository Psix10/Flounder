import test from 'node:test'
import assert from 'node:assert/strict'
import {
  resolveVenueTimeZone, venueTimeZoneLabel, formatVenueDate,
} from '../src/lib/venue-time.mjs'

test('venue timezone determines the calendar day and clock time', () => {
  const instant = '2026-11-21T23:30:00Z'
  const east = formatVenueDate(instant, 'Asia/Vladivostok')
  const moscow = formatVenueDate(instant, 'Europe/Moscow')
  assert.match(east, /22 ноября 2026/)
  assert.match(east, /09:30/)
  assert.match(moscow, /22 ноября 2026/)
  assert.match(moscow, /02:30/)
})

test('equivalent instants with different offsets have identical display', () => {
  assert.equal(
    formatVenueDate('2026-11-21T23:30:00Z', 'Asia/Vladivostok'),
    formatVenueDate('2026-11-22T02:30:00+03:00', 'Asia/Vladivostok'),
  )
})

test('seasonal timezone rules are applied to the actual instant', () => {
  assert.match(formatVenueDate('2026-01-15T12:00:00Z', 'Europe/Berlin'), /13:00/)
  assert.match(formatVenueDate('2026-07-15T12:00:00Z', 'Europe/Berlin'), /14:00/)
})

test('missing or invalid venue timezone explicitly falls back to UTC', () => {
  for (const zone of [null, undefined, '', ' ', 'Invalid/Zone', {}, 3]) {
    assert.equal(resolveVenueTimeZone(zone).timeZone, 'UTC')
    assert.match(venueTimeZoneLabel(zone), /UTC.*не указан или некорректен/)
    assert.match(formatVenueDate('2026-11-21T23:30:00Z', zone), /21 ноября 2026/)
    assert.match(formatVenueDate('2026-11-21T23:30:00Z', zone), /23:30/)
  }
})

test('configured UTC is not described as missing venue configuration', () => {
  assert.equal(venueTimeZoneLabel('UTC'), 'Время площадки: UTC.')
  assert.equal(resolveVenueTimeZone(' Asia/Vladivostok ').timeZone, 'Asia/Vladivostok')
})

test('missing or invalid dates never use browser-local date interpretation', () => {
  for (const value of [null, undefined, '']) {
    assert.equal(formatVenueDate(value, 'UTC'), 'Не указано')
  }
  for (const value of [
    'broken', '2026-11-21', '2026-11-21T12:00:00',
    '2026-99-99T12:00:00Z', 0, {},
  ]) {
    assert.equal(formatVenueDate(value, 'UTC'), 'Некорректная дата')
  }
})
