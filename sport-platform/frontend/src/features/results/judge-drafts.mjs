export const SPECIAL_STATUSES = Object.freeze(['DID_NOT_START', 'DID_NOT_FINISH', 'DISQUALIFIED'])
export function isSpecial(status) { return SPECIAL_STATUSES.includes(status) }
export function canEditEntry(entry) {
  return entry.resultStatus == null || ['PENDING', 'VALID', ...SPECIAL_STATUSES].includes(entry.resultStatus)
}
export function inferResultType(entries) {
  const types = [...new Set(entries.map(e => e.resultType).filter(t => t === 'TIME' || t === 'POINTS'))]
  return { type: types.length === 1 ? types[0] : 'TIME', mixed: types.length > 1 }
}
export function draftFromEntry(entry, fallback = 'TIME') {
  return { rawValue: entry.rawValue ?? '', status: isSpecial(entry.resultStatus) ? entry.resultStatus : 'PENDING',
    resultType: entry.resultType === 'POINTS' || entry.resultType === 'TIME' ? entry.resultType : fallback }
}
export function createDrafts(entries, fallback = 'TIME') {
  return Object.fromEntries(entries.map(e => [e.entryId, draftFromEntry(e, fallback)]))
}
function canonical(rawValue, type) {
  const raw = String(rawValue ?? '').trim()
  if (type === 'TIME') {
    const clock = /^(\d{2,}):([0-5]\d)\.(\d{2})$/.exec(raw)
    if (clock) {
      const value = Number(clock[1]) * 6000 + Number(clock[2]) * 100 + Number(clock[3])
      if (Number.isSafeInteger(value)) return `time:${value}`
    }
    return `raw:${raw}`
  }
  if (/^[+-]?\d+(?:\.\d+)?$/.test(raw)) {
    const negative = raw.startsWith('-')
    const [integer, fraction = ''] = raw.replace(/^[+-]/, '').split('.')
    const whole = integer.replace(/^0+(?=\d)/, '')
    const decimal = fraction.replace(/0+$/, '')
    const zero = whole === '0' && !decimal
    return `points:${negative && !zero ? '-' : ''}${whole}${decimal ? '.' + decimal : ''}`
  }
  return `raw:${raw}`
}
export function sameDraft(left, right) {
  if (left.status !== right.status) return false
  if (isSpecial(left.status)) return true
  return left.resultType === right.resultType && canonical(left.rawValue, left.resultType) === canonical(right.rawValue, right.resultType)
}
export function isDirty(entry, draft, fallback = 'TIME') {
  if (!canEditEntry(entry)) return false
  const value = draft ?? draftFromEntry(entry, fallback)
  if (entry.resultStatus == null && !entry.rawValue?.trim() && value.status === 'PENDING' && !value.rawValue.trim()) return false
  return !sameDraft(draftFromEntry(entry, fallback), value)
}
export function dirtyIds(entries, drafts, fallback = 'TIME') {
  return entries.filter(e => isDirty(e, drafts[e.entryId], fallback)).map(e => e.entryId)
}
export function validateDraft(draft) {
  if (!draft || !['TIME', 'POINTS'].includes(draft.resultType)) return 'Неизвестный тип результата.'
  if (isSpecial(draft.status)) return null
  if (draft.status !== 'PENDING') return 'Неизвестный статус ввода.'
  const raw = draft.rawValue.trim()
  if (!raw) return 'Введите результат; удаление сохранённого результата этой формой не выполняется.'
  if (draft.resultType === 'TIME') {
    if (!/^\d{2,}:[0-5]\d\.\d{2}$/.test(raw) || !canonical(raw, 'TIME').startsWith('time:')) {
      return 'Введите время в формате 01:03.25: секунды 00–59, две цифры сотых.'
    }
  } else if (!/^[+-]?\d+(?:\.\d+)?$/.test(raw) || !Number.isFinite(Number(raw))) {
    return 'Введите конечное число очков; десятичный разделитель — точка.'
  }
  return null
}
export function buildSavePlan(entries, drafts, fallback = 'TIME', onlyIds = null) {
  const wanted = onlyIds == null ? null : new Set(onlyIds)
  const changes = [], errors = {}
  for (const entry of entries) {
    if ((wanted && !wanted.has(entry.entryId)) || !isDirty(entry, drafts[entry.entryId], fallback)) continue
    const draft = drafts[entry.entryId]
    const error = validateDraft(draft)
    if (error) { errors[entry.entryId] = error; continue }
    changes.push(Object.freeze({ entryId: entry.entryId, participantName: entry.participantName,
      resultType: draft.resultType, payload: Object.freeze(isSpecial(draft.status)
        ? { competitionUnitEntryId: entry.entryId, rawValue: '', status: draft.status }
        : { competitionUnitEntryId: entry.entryId, rawValue: draft.rawValue.trim() }) }))
  }
  return { changes: Object.keys(errors).length ? [] : changes, errors }
}
export function mergeDrafts(before, after, drafts, fallback = 'TIME') {
  const previous = new Map(before.map(e => [e.entryId, e]))
  const currentIds = new Set(after.map(e => e.entryId))
  const next = {}, conflicts = [], orphaned = []
  for (const entry of after) {
    const old = previous.get(entry.entryId), draft = drafts[entry.entryId]
    const remote = draftFromEntry(entry, fallback)
    if (old && draft && (isDirty(old, draft, fallback) || (!canEditEntry(old) && !sameDraft(draftFromEntry(old, fallback), draft)))) {
      if (sameDraft(remote, draft)) next[entry.entryId] = remote
      else {
        next[entry.entryId] = draft
        if (!canEditEntry(entry) || !sameDraft(draftFromEntry(old, fallback), remote)) conflicts.push(entry.entryId)
      }
    } else next[entry.entryId] = remote
  }
  for (const entry of before) {
    if (!currentIds.has(entry.entryId) && isDirty(entry, drafts[entry.entryId], fallback)) {
      next[entry.entryId] = drafts[entry.entryId]
      orphaned.push(entry.entryId)
    }
  }
  for (const [id, draft] of Object.entries(drafts)) {
    if (!currentIds.has(id) && !previous.has(id)) { next[id] = draft; orphaned.push(id) }
  }
  return { drafts: next, conflicts, orphaned }
}
export function applyResultResponse(unit, response) {
  if (!response || !unit.entries.some(e => e.entryId === response.competitionUnitEntryId)) {
    throw new Error('Сервер вернул результат другой строки. Нужна сверка данных.')
  }
  return { ...unit, entries: unit.entries.map(e => e.entryId === response.competitionUnitEntryId
    ? { ...e, rawValue: response.rawValue, resultType: response.resultType,
      resultStatus: response.status, finalPlace: response.finalPlace } : e) }
}
export function isComplete(entry) {
  return isSpecial(entry.resultStatus) || Boolean(entry.rawValue?.trim())
}
export async function saveSequentially(changes, save, shouldContinue = () => true) {
  const savedIds = []
  for (let index = 0; index < changes.length; index++) {
    const change = changes[index]
    if (!shouldContinue()) return { savedIds, failedId: null, error: null, remainingIds: changes.slice(index).map(c => c.entryId), stopped: true }
    try { await save(change) }
    catch (error) { return { savedIds, failedId: change.entryId, error, remainingIds: changes.slice(index).map(c => c.entryId), stopped: false } }
    savedIds.push(change.entryId)
  }
  return { savedIds, failedId: null, error: null, remainingIds: [], stopped: false }
}
