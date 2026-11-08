const messages = {
  'results.final_not_ready': 'Итог пока нельзя подтвердить: проверьте публикацию всех заплывов, результаты и назначение подтверждённых заявок.',
  'results.final_already_confirmed': 'Итог уже подтверждён. Обновите данные; для исправлений сначала снимите финализацию.',
  'results.final_request_conflict': 'Эта попытка уже обработана с другими данными. Обновите состояние перед новым действием.',
  'results.discipline_finalized': 'Итог дисциплины подтверждён. Организатор должен снять финализацию перед изменением результатов.',
  'results.final_history_immutable': 'Сохранённую историю итогов нельзя перезаписывать или удалять.',
  'results.final_isolation_not_supported': 'Сервер отклонил режим транзакции. Передайте организатору код ошибки; повторное нажатие не исправит настройку.',
  'results.mixed_category_assignments': 'В опубликованных заплывах смешаны назначения с категориями и без них. Требуется проверка протокола.',
}
export function readApiFailure(payload, status) {
  const source = payload && typeof payload === 'object' ? payload : {}
  const violations = Array.isArray(source.violations) ? source.violations.filter(v =>
    v && typeof v.field === 'string' && typeof v.message === 'string').map(v => ({field:v.field,message:v.message})) : []
  return {message:typeof source.message === 'string' ? source.message : `Request failed with HTTP ${status}`,
    code:typeof source.code === 'string' ? source.code : undefined,violations}
}
export function failureMessage(error, fallback = 'Не удалось выполнить запрос.') {
  if (error?.status === 401) return 'Сессия истекла. Войдите в систему снова.'
  if (error?.status === 403) return 'Нет права управления этой дисциплиной. Роль организатора сама по себе не даёт доступ ко всем событиям.'
  const friendly = typeof error?.code === 'string' && Object.hasOwn(messages,error.code) ? messages[error.code] : undefined
  return friendly ?? (typeof error?.message === 'string' && error.message ? error.message : fallback)
}
export function isMissingFinal(error) {
  return error?.status === 404 && error?.code === 'results.final_not_found'
}
export function keepAttemptAfterFailure(error) {
  return !(typeof error?.status === 'number' && error.status >= 400 && error.status < 500 && ![408,429].includes(error.status))
}
export function categoryOptions(entries) {
  return [...new Set(entries.map(e=>e.categoryCode).filter(v=>typeof v === 'string' && v.length>0))].sort((a,b)=>a.localeCompare(b,'ru'))
}
export function filterStandings(entries, category) {
  if (!category) return [...entries]
  return entries.filter(e=>category === '__legacy__' ? e.categoryCode == null : e.categoryCode === category)
}
export function formatTimestamp(value) {
  if (!value || !Number.isFinite(new Date(value).getTime())) return '—'
  return new Intl.DateTimeFormat('ru-RU',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value))
}
