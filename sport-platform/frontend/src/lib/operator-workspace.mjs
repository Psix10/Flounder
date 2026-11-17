const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function id(value) { return typeof value === 'string' && UUID.test(value) }
function text(value) { return typeof value === 'string' && value.trim().length > 0 }
function optionalText(value) { return value == null || typeof value === 'string' }
function rows(value, validate, message) {
  if (!Array.isArray(value)) throw new Error(message)
  const seen = new Set()
  for (const row of value) {
    if (!row || !id(row.id) || seen.has(row.id) || !validate(row)) throw new Error(message)
    seen.add(row.id)
  }
  return value
}
export function validateOperatorEvents(value) {
  return rows(value, row => text(row.title) && text(row.status) && optionalText(row.eventStartAt) &&
    optionalText(row.eventEndAt) && optionalText(row.publicSlug), 'API не вернул корректный список назначенных мероприятий.')
}
export function validateOperatorEvent(value, eventId) {
  validateOperatorEvents([value])
  if (value.id !== eventId) throw new Error('Ответ относится к другому мероприятию.')
  return value
}
export function validateOperatorDisciplines(value, eventId) {
  return rows(value, row => row.eventId === eventId && text(row.name) && text(row.status) &&
    text(row.competitionFormat) && optionalText(row.unitType) && optionalText(row.resultType),
    'Список дисциплин имеет неверный формат или относится к другому мероприятию.')
}
export function validateOperatorUnits(value, disciplineId) {
  return rows(value, row => row.eventDisciplineId === disciplineId && text(row.label) && text(row.status) &&
    (row.sequenceNumber == null || Number.isInteger(row.sequenceNumber)) && optionalText(row.scheduledAt),
    'Список заплывов имеет неверный формат или относится к другой дисциплине.')
}
function requireId(value) { if (!id(value)) throw new Error('Некорректный идентификатор в маршруте.'); return encodeURIComponent(value) }
export function operatorEventPath(eventId) { return `/operator/events/${requireId(eventId)}` }
export function operatorDisciplinePath(eventId, disciplineId) { return `${operatorEventPath(eventId)}/disciplines/${requireId(disciplineId)}` }
export function operatorJudgePath(eventId, disciplineId, unitId) {
  operatorDisciplinePath(eventId, disciplineId)
  const query = new URLSearchParams({ eventId, disciplineId })
  return `/operator/competition-units/${requireId(unitId)}?${query}`
}
export function operatorResultsBackPath(eventId, disciplineId, actualDisciplineId, isOperator = false) {
  if (!isOperator) return '/operator/results'
  if (id(eventId) && id(disciplineId) && disciplineId === actualDisciplineId) return operatorDisciplinePath(eventId, disciplineId)
  return '/operator/events'
}
