export const ALL_CATEGORIES = ':all:'
export const LEGACY_CATEGORY = ':legacy:'

export function isMissingFinal(error) {
  return error?.status === 404 && error?.code === 'results.final_not_found'
}
export function isUncertainFailure(error) {
  const status = error?.status
  return !(typeof status === 'number' && status >= 400 && status < 500 && status !== 408 && status !== 429)
}
export function protocolErrorMessage(error) {
  if (error?.status === 401) return 'Сессия истекла или не подтверждена. Войдите заново и обновите страницу.'
  if (error?.status === 403) return 'Сервер не разрешил управление этой дисциплиной. Роль организатора сама по себе не даёт доступ к чужому событию.'
  const messages = {
    'results.final_not_ready': 'Итог пока нельзя подтвердить: проверьте публикацию всех заплывов, результаты и назначение всех подтверждённых заявок.',
    'results.final_already_confirmed': 'Итог уже подтверждён. Обновите данные; для исправления сначала явно снимите финализацию.',
    'results.final_request_conflict': 'Сервер отклонил повторный ключ с другими данными. Обновите состояние перед новым действием.',
    'results.mixed_category_assignments': 'В опубликованных результатах смешаны назначения с категориями и без них. Исправьте протокол на сервере.',
    'results.invalid_category_assignment': 'Обнаружена некорректная пара категории и группы посева.',
    'results.discipline_finalized': 'Дисциплина финализирована: изменение заблокировано до явного снятия финализации.',
    'results.final_isolation_not_supported': 'Сервер отклонил уровень изоляции транзакции. Требуется проверка конфигурации backend.',
  }
  return messages[error?.code] || (typeof error?.message === 'string' ? error.message : 'Не удалось выполнить запрос. Проверьте соединение и обновите данные.')
}
export function createConfirmationAttempt(note, programmeComplete, createId = () => globalThis.crypto.randomUUID()) {
  if (programmeComplete !== true) throw new Error('Явно подтвердите завершённость всей программы дисциплины.')
  const normalized = note.trim()
  if (normalized.length > 500) throw new Error('Примечание не должно превышать 500 символов.')
  let requestId
  try { requestId = createId() } catch { throw new Error('Не удалось создать безопасный requestId. Откройте интерфейс через HTTPS или localhost.') }
  if (typeof requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(requestId)) {
    throw new Error('Не удалось создать корректный requestId.')
  }
  return Object.freeze({ requestId, programmeComplete: true, note: normalized })
}
export function createRevocationAttempt(snapshot, reason) {
  const normalized = reason.trim()
  if (!normalized || normalized.length > 1000) throw new Error('Укажите причину снятия: от 1 до 1000 символов.')
  return Object.freeze({ id: snapshot.id, revision: snapshot.revision, reason: normalized })
}
export function unitsReady(units) {
  return Array.isArray(units) && units.length > 0 && units.every(unit => unit.status === 'PUBLISHED')
}
export function canConfirm(flags) {
  return flags.permission && !flags.loading && !flags.busy && flags.finalKnown && !flags.otherPending &&
    (flags.pending || (!flags.active && flags.unitsReady && flags.complete))
}
export function categoryOptions(entries) {
  return [...new Set(entries.map(entry => entry.categoryCode ?? LEGACY_CATEGORY))].sort()
}
export function filterCategory(entries, category) {
  if (category === ALL_CATEGORIES) return entries
  return entries.filter(entry => (entry.categoryCode ?? LEGACY_CATEGORY) === category)
}
export function statusLabel(status) {
  return ({ VALID: 'Учтён', DID_NOT_START: 'Не стартовал', DID_NOT_FINISH: 'Не финишировал', DISQUALIFIED: 'Дисквалифицирован', PENDING: 'Ожидает проверки' })[status] || status
}
