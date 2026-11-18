// OPERATOR_WORKSPACE_BACK_V1
import { operatorResultsBackPath } from '../../lib/operator-workspace.mjs'
// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  changeCompetitionUnitPublication, getCompetitionUnitDetails, recalculatePlaces, recordResult,
  type CompetitionUnitDetailsResponse, type ResultType,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import {
  buildSavePlan, canEditEntry, createDrafts, dirtyIds, draftFromEntry,
  inferResultType, isComplete, isDirty, isSpecial, mergeDrafts, sameDraft, saveSequentially,
} from './judge-drafts.mjs'
import type { Drafts, EditableStatus, Draft } from './judge-drafts.mjs'
import styles from './JudgePanelPage.module.css'
import extra from './JudgeDrafts.module.css'
// MUTATION_READBACK_FIX_V1
import { assertUnitSnapshot, mutateAndReadUnit } from './mutation-readback.mjs'

const STATUS_OPTIONS: { value: EditableStatus; label: string }[] = [
  { value: 'PENDING', label: 'Обычный результат' },
  { value: 'DID_NOT_START', label: 'DNS — не стартовал' },
  { value: 'DID_NOT_FINISH', label: 'DNF — не финишировал' },
  { value: 'DISQUALIFIED', label: 'DSQ — дисквалифицирован' },
]
function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.code === 'results.discipline_finalized') return 'Итог дисциплины подтверждён. Для исправлений организатор должен отдельно снять финализацию.'
    if (error.status === 401) return 'Сессия истекла. Войдите снова.'
    if (error.status === 403) return 'Нет прав на это действие с результатами.'
    if (error.status === 404) return 'Заплыв или строка результата не найдены.'
    return [error.message || fallback, ...error.violations.map(v => `${v.field}: ${v.message}`)].join('\n')
  }
  return error instanceof Error ? error.message : fallback
}
function statusLabel(status: string | null): string {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта', VALID: 'Учтён', DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал', DISQUALIFIED: 'Дисквалифицирован',
    CORRECTED: 'Исправлен', EXHIBITION: 'Вне зачёта',
  }
  return status ? labels[status] ?? status : 'Нет результата'
}
function resultClass(status: string | null) {
  const tone = status === 'VALID' ? styles.resultStatusValid : status === 'DISQUALIFIED' ? styles.resultStatusDanger
    : status === 'DID_NOT_START' || status === 'DID_NOT_FINISH' ? styles.resultStatusWarning
      : status === 'CORRECTED' ? styles.resultStatusCorrected : status === 'EXHIBITION' ? styles.resultStatusNeutral : styles.resultStatusPending
  return `${styles.resultStatus} ${tone}`
}
function unitLabel(status: string) { return status === 'PUBLISHED' ? 'Опубликовано' : status === 'DRAFT' ? 'Черновик' : status }

export function JudgePanelPage() {
  const { session } = useAuth()
  const { unitId } = useParams<{ unitId: string }>()
  const navigate = useNavigate()
  useEffect(() => {
    if (!session?.accessToken) navigate('/login', { replace: true })
  }, [session?.accessToken, navigate])
  if (!session?.accessToken) return null
  if (!unitId) return <p role="alert">Идентификатор заплыва не указан.</p>
  return <JudgePanelContent key={`${unitId}:${session.accessToken}`} unitId={unitId} token={session.accessToken} />
}
function JudgePanelContent({ unitId, token }: { unitId: string; token: string }) {
  const { hasRole } = useAuth()
  const [operatorSearchParams] = useSearchParams()
  const canRecord = hasRole('platform_admin') || hasRole('operator') || hasRole('organizer')
  const canPublish = canRecord
  const [unit, setUnit] = useState<CompetitionUnitDetailsResponse | null>(null)
  const [drafts, setDrafts] = useState<Drafts>({})
  const [resultType, setResultType] = useState<ResultType>('TIME')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [savingId, setSavingId] = useState<string | null>(null)
  const [progress, setProgress] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const [needsReview, setNeedsReview] = useState(false)
  const [reviewed, setReviewed] = useState(false)
  const [needsRecalculation, setNeedsRecalculation] = useState(false)
  const [orphans, setOrphans] = useState<string[]>([])
  const [conflictIds, setConflictIds] = useState<string[]>([])
  const locked = useRef(false)
  const reviewRequired = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    let active = true
    mounted.current = true
    async function load() {
      try {
        const loaded = await getCompetitionUnitDetails(unitId, token)
        if (!active) return
        assertUnitSnapshot(loaded, unitId)
        const inferred = inferResultType(loaded.entries)
        setUnit(loaded); setResultType(inferred.type); setDrafts(createDrafts(loaded.entries, inferred.type))
      } catch (failure) { if (active) setError(errorText(failure, 'Не удалось загрузить результаты.')) }
      finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false; mounted.current = false }
  }, [unitId, token])

  const entries = useMemo(() => [...(unit?.entries ?? [])].sort((a,b) =>
    (a.laneOrPosition ?? Number.MAX_SAFE_INTEGER) - (b.laneOrPosition ?? Number.MAX_SAFE_INTEGER)), [unit])
  const changedIds = dirtyIds(entries, drafts, resultType)
  const published = unit?.status === 'PUBLISHED'
  const draftUnit = unit?.status === 'DRAFT'
  const completeCount = entries.filter(isComplete).length
  const pending = entries.some(e => e.resultStatus === 'PENDING' && Boolean(e.rawValue?.trim()))
  const mixedTypes = inferResultType(entries).mixed
  const typeMismatch = entries.some(e => Boolean(e.rawValue?.trim()) && e.resultType != null && e.resultType !== resultType)
  const rankStale = !published && (needsRecalculation || pending)
  const noUnsavedChanges = changedIds.length === 0 && orphans.length === 0 && conflictIds.length === 0

  useEffect(() => {
    if (noUnsavedChanges && !needsReview) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [noUnsavedChanges, needsReview])

  function requireReview() {
    reviewRequired.current = true; setNeedsReview(true); setReviewed(false)
  }
  function acquire(allowReview = false) {
    if (locked.current || loading || (!allowReview && reviewRequired.current)) return false
    locked.current = true; setBusy(true); setError(null); setSuccess(null)
    return true
  }
  function release() {
    locked.current = false
    if (mounted.current) { setBusy(false); setSavingId(null); setProgress(null) }
  }
  function applySnapshot(before: CompetitionUnitDetailsResponse, next: CompetitionUnitDetailsResponse, local: Drafts) {
    assertUnitSnapshot(next, unitId)
    const merged = mergeDrafts(before.entries, next.entries, local, resultType)
    if (!mounted.current) return
    setUnit(next); setDrafts(merged.drafts); setOrphans(merged.orphaned); setConflictIds(merged.conflicts)
    if (!merged.conflicts.length && !merged.orphaned.length && dirtyIds(next.entries,merged.drafts,resultType).length === 0) {
      const inferred = inferResultType(next.entries)
      if (!inferred.mixed) setResultType(inferred.type)
    }
    if (merged.conflicts.length || merged.orphaned.length) {
      requireReview()
      setError('Состав или сохранённые значения изменились на сервере. Локальный ввод сохранён; проверьте расхождения перед продолжением.')
    }
  }
  function edit(entryId: string, changes: Partial<Draft>) {
    if (!unit || busy || published || !canRecord) return
    setDrafts(current => ({ ...current, [entryId]: { ...current[entryId], ...changes } }))
    setRowErrors(current => { const next = { ...current }; delete next[entryId]; return next })
  }

  async function save(onlyIds?: string[]) {
    if (!unit || !canRecord || !draftUnit || locked.current || reviewRequired.current) return
    const plan = buildSavePlan(entries, drafts, resultType, onlyIds ?? null)
    if (Object.keys(plan.errors).length) {
      setRowErrors(plan.errors); setError('Исправьте отмеченные строки. Ни один запрос этого сохранения не отправлен.'); setSuccess(null); return
    }
    if (!plan.changes.length || !acquire()) return
    setRowErrors({})
    let workingUnit = unit
    let workingDrafts = { ...drafts }
    let accepted = 0
    try {
      const outcome = await saveSequentially(plan.changes, async change => {
        if (mounted.current) { setSavingId(change.entryId); setProgress(`Сохранено ${accepted} из ${plan.changes.length}`) }
        const fresh = await mutateAndReadUnit(
          () => recordResult(change.payload, { resultType: change.resultType, accessToken: token }),
          () => getCompetitionUnitDetails(unitId, token), unitId, 'DRAFT',
        )
        const acknowledged = fresh.entries.find(e => e.entryId === change.entryId)
        if (!acknowledged || acknowledged.resultType !== change.resultType || !canEditEntry(acknowledged) ||
            !sameDraft(draftFromEntry(acknowledged, change.resultType), {
              rawValue: change.payload.rawValue,
              status: change.payload.status ?? 'PENDING', resultType: change.resultType,
            })) {
          throw new Error('После записи серверные значения не совпали с отправленной строкой. Запись не повторяется; нужна сверка.')
        }
        const merged = mergeDrafts(workingUnit.entries, fresh.entries, workingDrafts, resultType)
        if (merged.conflicts.length || merged.orphaned.length) {
          throw new Error('При чтении после записи обнаружены другие изменения на сервере. Остальные строки не отправляются; нужна сверка.')
        }
        workingUnit = fresh
        workingDrafts = merged.drafts
        accepted++
        if (mounted.current) { setUnit(workingUnit); setDrafts(workingDrafts); setNeedsRecalculation(true) }
      }, () => mounted.current)
      if (!mounted.current) return
      if (outcome.failedId) {
        const detail = errorText(outcome.error, 'Не удалось подтвердить сохранение строки.')
        setRowErrors({ [outcome.failedId]: detail })
        setError(`Подтверждено сохранение ${outcome.savedIds.length} из ${plan.changes.length}. ` +
          'На первой ошибке отправка остановлена. Ошибочная строка могла сохраниться; остальные запросы не отправлены.\n' + detail)
        requireReview()
      }
      try {
        const actual = await getCompetitionUnitDetails(unitId, token)
        applySnapshot(workingUnit, actual, workingDrafts)
        if (!outcome.failedId && mounted.current) setSuccess(
          `Сохранено строк: ${outcome.savedIds.length}. Остальные черновики сохранены в форме. Перед публикацией пересчитайте места.`)
      } catch (failure) {
        if (mounted.current) {
          requireReview()
          setError(`Получено успешных ответов на запись: ${outcome.savedIds.length}. ` +
            'Повторное чтение не завершилось; несохранённый ввод оставлен в форме. Сверьте данные перед продолжением.\n' +
            errorText(failure, 'Не удалось прочитать состояние.'))
        }
      }
    } finally { release() }
  }

  async function refresh() {
    if (!acquire(true)) return
    try {
      const actual = await getCompetitionUnitDetails(unitId, token)
      if (!mounted.current) return
      assertUnitSnapshot(actual, unitId)
      if (unit) applySnapshot(unit, actual, drafts)
      else {
        const inferred = inferResultType(actual.entries)
        setUnit(actual); setResultType(inferred.type); setDrafts(createDrafts(actual.entries,inferred.type))
      }
      setReviewed(true)
      setSuccess('Данные прочитаны с сервера. Несохранённые локальные изменения сохранены в форме.')
    } catch (failure) {
      if (mounted.current) { requireReview(); setError(errorText(failure,'Не удалось обновить данные.')) }
    } finally { release() }
  }
  async function recalculate() {
    if (!unit || !canRecord || !draftUnit || !noUnsavedChanges || mixedTypes || typeMismatch || !entries.length || !acquire()) return
    try {
      const actual = await mutateAndReadUnit(
        () => recalculatePlaces(unitId, { rankingStrategy: resultType === 'TIME' ? 'ASC' : 'DESC', accessToken: token }),
        () => getCompetitionUnitDetails(unitId, token), unitId, 'DRAFT',
      )
      applySnapshot(unit, actual, drafts)
      if (mounted.current) { setNeedsRecalculation(false); setSuccess('Места успешно пересчитаны.') }
    } catch (failure) {
      if (mounted.current) { requireReview(); setError(errorText(failure,'Не удалось подтвердить пересчёт.')) }
    } finally { release() }
  }
  async function publication() {
    if (!unit || !canPublish || !noUnsavedChanges || (!published && (!draftUnit || completeCount !== entries.length || !entries.length || rankStale || mixedTypes || typeMismatch)) || locked.current || reviewRequired.current) return
    const willPublish = !published
    if (!window.confirm(willPublish
      ? `Опубликовать результаты «${formatHeatLabel(unit.label)}»? Участников: ${entries.length}.`
      : `Снять результаты «${formatHeatLabel(unit.label)}» с публикации? При подтверждённом итоге дисциплины сервер отклонит действие.`)) return
    if (!acquire()) return
    try {
      const actual = await mutateAndReadUnit(
        () => changeCompetitionUnitPublication(unitId, willPublish, token),
        () => getCompetitionUnitDetails(unitId, token), unitId, willPublish ? 'PUBLISHED' : 'DRAFT',
      )
      applySnapshot(unit, actual, drafts)
      if (mounted.current) setSuccess(willPublish ? 'Результаты опубликованы.' : 'Публикация снята.')
    } catch (failure) {
      if (mounted.current) { requireReview(); setError(errorText(failure,'Не удалось подтвердить изменение публикации.')) }
    } finally { release() }
  }
  function changeType(next: ResultType) {
    if (!unit || !draftUnit || !canRecord || busy || reviewRequired.current || next === resultType) return
    if (entries.some(e => e.rawValue?.trim()) && !window.confirm(
      'Сменить тип ввода? Изменения обычных результатов потребуется проверить и сохранить. Сервер проверит совместимость с дисциплиной.')) return
    setResultType(next)
    setDrafts(current => Object.fromEntries(Object.entries(current).map(([id, draft]) => {
      const entry = entries.find(e => e.entryId === id)
      return [id, entry && draft.status === 'PENDING' && canEditEntry(entry) ? { ...draft, resultType: next } : draft]
    })))
    setRowErrors({})
  }

  return <section className={styles.page} aria-busy={busy}>
    <Link className={styles.backLink} to={operatorResultsBackPath(operatorSearchParams.get('eventId'), operatorSearchParams.get('disciplineId'), unit?.eventDisciplineId, hasRole('operator'))} onClick={event => {
      if (busy || ((!noUnsavedChanges || needsReview) && !window.confirm('Есть несохранённые изменения или непроверенная операция. Покинуть страницу?'))) event.preventDefault()
    }}>← К списку заплывов</Link>
    <div className={styles.titleRow}>
      <div><p className={styles.eyebrow}>Судейская панель</p><h1 className={styles.title}>{unit ? formatHeatLabel(unit.label) : 'Результаты'}</h1></div>
      {unit ? <span className={`${styles.unitStatus} ${published ? styles.unitStatusPublished : styles.unitStatusDraft}`}>{unitLabel(unit.status)}</span> : null}
    </div>
    <p className={styles.description}>Заполните строки и сохраните все изменения одной кнопкой либо каждую строку отдельно. Для времени используйте <code>01:03.25</code>. После сохранения пересчитайте места.</p>
    {error ? <p className={styles.errorMessage} role="alert" style={{whiteSpace:'pre-line'}}>{error}</p> : null}
    {success ? <p className={styles.successMessage} role="status">{success}</p> : null}
    <div className={extra.controls}>
      <button className={extra.button} type="button" disabled={busy || loading} onClick={() => void refresh()}>Сверить с сервером</button>
      {progress ? <p className={extra.status} role="status">{progress}</p> : null}
    </div>
    {loading ? <div className={styles.stateCard} role="status">Загружаем результаты…</div> : null}
    {needsReview ? <div className={extra.notice} role="status">
      <p>Изменяющие действия остановлены. Сначала нажмите «Сверить с сервером»: предыдущий запрос мог выполниться даже без ответа. Автоматического повтора нет.</p>
      {reviewed ? <button type="button" className={extra.button} disabled={busy || orphans.length > 0} onClick={() => {
        if (window.confirm('Вы проверили актуальные серверные значения и оставшиеся черновики? При неизвестном результате запроса не продолжайте, пока не убедитесь, что он завершился.')) {
          reviewRequired.current = false; setNeedsReview(false); setReviewed(false); setRowErrors({}); setError(null)
        }
      }}>Данные проверены — разрешить дальнейшие действия</button> : null}
    </div> : null}
    {orphans.length ? <div className={extra.notice}>
      <p>Строки с локальными изменениями исчезли из серверного списка. Эти значения не отправляются; сохраните их отдельно перед уходом.</p>
      <pre className={extra.orphans}>{JSON.stringify(Object.fromEntries(orphans.map(id => [id,drafts[id]])),null,2)}</pre>
    </div> : null}
    {!loading && unit ? <>
      <div className={styles.summaryCard}>
        <div><span className={styles.summaryLabel}>Статус</span><strong>{unitLabel(unit.status)}</strong></div>
        <div><span className={styles.summaryLabel}>Участников</span><strong>{entries.length}</strong></div>
        <div><span className={styles.summaryLabel}>Сохранено результатов</span><strong>{completeCount} / {entries.length}</strong></div>
        <div className={styles.disciplineSummary}><span className={styles.summaryLabel}>ID дисциплины</span><code>{unit.eventDisciplineId}</code></div>
      </div>
      {published ? <div className={styles.publishedNotice} role="status">
        <div><strong>Результаты опубликованы</strong><p>Ввод и пересчёт заблокированы. Для просмотра показаны сохранённые серверные значения.</p></div>
        {canPublish ? <button className={styles.unpublishButton} type="button" disabled={busy || needsReview || !noUnsavedChanges} onClick={() => void publication()}>Снять с публикации</button> : null}
      </div> : null}
      {!canRecord ? <p className={extra.notice}>Ввод результатов доступен организатору мероприятия, назначенному оператору и администратору платформы. Права на конкретное мероприятие дополнительно проверяет сервер.</p> : null}
      <section className={styles.toolbarCard}>
        <h2>Настройки результата</h2>
        <div className={styles.toolbarControls}>
          <label className={styles.field}><span>Тип результата</span><select disabled={busy || !draftUnit || !canRecord || needsReview} value={resultType} onChange={event => changeType(event.target.value as ResultType)}>
            <option value="TIME">Время — меньше лучше</option><option value="POINTS">Очки — больше лучше</option>
          </select></label>
          <button className={styles.recalculateButton} type="button" disabled={busy || !draftUnit || !canRecord || needsReview || !noUnsavedChanges || !entries.length || mixedTypes || typeMismatch} onClick={() => void recalculate()}>Пересчитать места</button>
          {!published && canPublish ? <button className={styles.publishButton} type="button" disabled={busy || !draftUnit || needsReview || !noUnsavedChanges || !entries.length || completeCount !== entries.length || rankStale || mixedTypes || typeMismatch} onClick={() => void publication()}>Опубликовать результаты</button> : null}
        </div>
        {!noUnsavedChanges ? <p className={styles.recalculateHint}>Есть несохранённые изменения. Сначала сохраните или явно отмените их; пересчёт и публикация недоступны.</p> : rankStale && !published ? <p className={styles.recalculateHint}>После сохранения пересчитайте места перед публикацией.</p> : null}
        {typeMismatch && !mixedTypes ? <p className={extra.notice}>Выбранный тип отличается от типа сохранённых значений. Сохраните намеренное изменение типа либо верните прежний тип перед пересчётом.</p> : null}
        {mixedTypes ? <p className={extra.notice}>В сохранённых строках разные типы результата. Проверьте данные; автоматический выбор порядка мест заблокирован.</p> : null}
      </section>
      <section className={styles.entriesSection}>
        <div className={styles.sectionHeader}><div><h2>Результаты участников</h2><p>Несохранённых строк: {changedIds.length}. Строки сохраняются по очереди. При ошибке уже сохранённые строки не изменятся, а следующие не будут отправлены.</p></div><span className={styles.entriesCount}>Участников: {entries.length}</span></div>
        {!published && canRecord ? <div className={extra.controls}>
          <button className={`${extra.button} ${extra.primary}`} type="button" disabled={busy || needsReview || !draftUnit || !changedIds.length} onClick={() => void save()}>Сохранить все изменения ({changedIds.length})</button>
          <button className={extra.button} type="button" disabled={busy || (changedIds.length === 0 && conflictIds.length === 0 && orphans.length === 0)} onClick={() => {
            if (window.confirm('Отменить все локальные изменения, включая черновики исчезнувших строк, и вернуть последние прочитанные серверные значения?')) {
              setDrafts(createDrafts(entries,inferResultType(entries).type)); setResultType(inferResultType(entries).type); setRowErrors({}); setConflictIds([]); setOrphans([]); setError(null)
            }
          }}>Отменить локальные изменения</button>
        </div> : null}
        {!entries.length ? <div className={styles.stateCard}>Участники пока не назначены.</div> : <div className={styles.tableWrap}><table className={styles.resultsTable}>
          <thead><tr><th>Место</th><th>Дорожка</th><th>Участник</th><th>Результат</th><th>Статус ввода</th><th>Сохранённый статус</th><th>Действия</th></tr></thead>
          <tbody>{entries.map(entry => {
            const draft = drafts[entry.entryId] ?? draftFromEntry(entry,resultType)
            const dirty = isDirty(entry,draft,resultType)
            const conflicted = conflictIds.includes(entry.entryId)
            const editable = canEditEntry(entry)
            const disabled = busy || !draftUnit || !canRecord || needsReview || !editable
            return <tr key={entry.entryId}>
              <td className={styles.placeCell}>{!published && (rankStale || dirty) ? '—' : entry.finalPlace ?? '—'}</td>
              <td className={styles.laneCell}>{entry.laneOrPosition ?? '—'}</td>
              <td><strong className={styles.participantName}>{entry.participantName}</strong><span className={styles.registrationId}>Заявка {entry.registrationId.slice(0,8)}</span>{dirty || conflicted ? <span className={extra.dirty}>{conflicted ? 'Расхождение с сервером' : 'Не сохранено'}</span> : null}{!editable ? <span className={extra.serverValue}>Специальный серверный статус: строка только для чтения.</span> : null}</td>
              <td><input className={styles.resultInput} type="text" spellCheck={false} aria-label={`Результат: ${entry.participantName}`} aria-invalid={Boolean(rowErrors[entry.entryId])}
                disabled={disabled || isSpecial(draft.status)} placeholder={isSpecial(draft.status) ? 'Не требуется' : resultType === 'TIME' ? '01:03.25' : '0'}
                value={published || !editable ? entry.rawValue ?? '' : isSpecial(draft.status) ? '' : draft.rawValue}
                onChange={event => edit(entry.entryId,{ rawValue:event.target.value })} />
                {dirty ? <span className={extra.serverValue}>На сервере: {entry.rawValue || statusLabel(entry.resultStatus)}</span> : null}
                {conflicted && !editable ? <span className={extra.rowError}>Неотправленный локальный черновик: {draft.rawValue || statusLabel(draft.status)}</span> : null}
                {rowErrors[entry.entryId] ? <span className={extra.rowError} role="alert">{rowErrors[entry.entryId]}</span> : null}
              </td>
              <td>{!editable ? statusLabel(entry.resultStatus) : <select className={styles.statusSelect} aria-label={`Статус ввода: ${entry.participantName}`} disabled={disabled} value={draft.status} onChange={event => edit(entry.entryId,{status:event.target.value as EditableStatus})}>
                {STATUS_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>}</td>
              <td><span className={resultClass(entry.resultStatus)}>{statusLabel(entry.resultStatus)}</span></td>
              <td><button className={styles.saveButton} type="button" disabled={disabled || !dirty} onClick={() => void save([entry.entryId])}>{savingId === entry.entryId ? 'Сохраняем…' : 'Сохранить строку'}</button></td>
            </tr>
          })}</tbody>
        </table></div>}
      </section>
    </> : null}
  </section>
}
