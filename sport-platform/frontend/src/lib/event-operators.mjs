const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export function normalizeOperatorEmail(input) {
  const email = typeof input === 'string' ? input.trim() : ''
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(email)) {
    throw new Error('Укажите корректный полный email оператора.')
  }
  return email
}
function validId(value) { return typeof value === 'string' && UUID.test(value) }
export function validateOperatorMembers(value, eventId) {
  if (!Array.isArray(value)) throw new Error('API не вернул список операторов. Выполните сверку.')
  const ids = new Set()
  for (const row of value) {
    if (!row || !validId(row.id) || row.eventId !== eventId || !validId(row.operatorUserId) ||
        !validId(row.assignedByUserId) || typeof row.createdAt !== 'string' ||
        typeof row.displayName !== 'string' || !row.displayName.trim() ||
        (row.email !== null && typeof row.email !== 'string') || typeof row.eligible !== 'boolean' || ids.has(row.operatorUserId)) {
      throw new Error('Список операторов имеет неверный формат или относится к другому мероприятию. Выполните сверку.')
    }
    ids.add(row.operatorUserId)
  }
  return value
}
export function validateOperatorCandidates(value, requestedEmail) {
  const normalized = normalizeOperatorEmail(requestedEmail)
  if (!Array.isArray(value) || value.length > 1) throw new Error('API вернул неоднозначный результат поиска оператора.')
  for (const row of value) {
    if (!row || !validId(row.userId) || typeof row.email !== 'string' ||
        row.email.toLowerCase() !== normalized.toLowerCase() ||
        typeof row.displayName !== 'string' || !row.displayName.trim() || typeof row.assigned !== 'boolean') {
      throw new Error('Ответ поиска не соответствует указанному email. Назначение остановлено.')
    }
  }
  return value
}
export async function changeAndReadOperators(write, read, eventId, operatorUserId, expectedAssigned) {
  await write()
  const rows = validateOperatorMembers(await read(), eventId)
  if (rows.some(row => row.operatorUserId === operatorUserId) !== expectedAssigned) {
    throw new Error('После операции назначение на сервере отличается от ожидаемого. Сверьте список; повторного запроса не было.')
  }
  return rows
}
