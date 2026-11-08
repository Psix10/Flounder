import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/http'
import {
  confirmFinal, revokeFinal,
  type ConfirmFinalRequest, type FinalStandings,
} from '../../api/standings.api'
import {
  canConfirm, createConfirmationAttempt, createRevocationAttempt,
  isUncertainFailure, protocolErrorMessage, unitsReady, type RevocationAttempt,
} from './protocol-model.mjs'
import { useProtocolData } from './useProtocolData'
import { StandingsTable } from './StandingsTable'
import styles from './ProtocolBoard.module.css'

function formatDate(value?: string | null) {
  const date = value ? new Date(value) : null
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(date) : '—'
}
function ErrorDetails({ error }: { error: unknown }) {
  const violations = error instanceof ApiError ? error.violations : []
  return <div className={styles.error} role="alert">
    <p>{protocolErrorMessage(error)}</p>
    {error instanceof ApiError && error.code ? <p>Код: {error.code}</p> : null}
    {violations.length > 0 ? <ul>{violations.filter(item => typeof item?.field === 'string' && typeof item?.message === 'string').map((item, index) =>
      <li key={index}>{item.field}: {item.message}</li>)}</ul> : null}
  </div>
}
function SnapshotInfo({ snapshot }: { snapshot: FinalStandings }) {
  return <div className={styles.meta}>
    <span>Ревизия № {snapshot.revision}. {snapshot.status === 'CONFIRMED' ? 'Подтверждена' : 'Финализация снята'}.</span>
    <span>Подтверждение: {formatDate(snapshot.confirmedAt)}. Автор (ID): {snapshot.confirmedBy}.</span>
    {snapshot.confirmationNote ? <span>Примечание: {snapshot.confirmationNote}</span> : null}
    {snapshot.status === 'REVOKED' ? <>
      <span>Снятие: {formatDate(snapshot.revokedAt)}. Автор (ID): {snapshot.revokedBy ?? '—'}.</span>
      <span>Причина: {snapshot.revocationReason ?? '—'}</span>
    </> : null}
  </div>
}

export function DisciplineProtocolBoard({ eventId, disciplineId, accessToken }: { eventId: string; disciplineId: string; accessToken?: string }) {
  const { data, refresh } = useProtocolData(eventId, disciplineId, accessToken)
  const [view, setView] = useState<'current' | 'final' | 'history'>('current')
  const [historyId, setHistoryId] = useState<string | null>(null)
  const [complete, setComplete] = useState(false)
  const [note, setNote] = useState('')
  const [reason, setReason] = useState('')
  const [pendingConfirm, setPendingConfirm] = useState<Readonly<ConfirmFinalRequest> | null>(null)
  const [pendingRevoke, setPendingRevoke] = useState<RevocationAttempt | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<unknown>(null)
  const [message, setMessage] = useState('')
  const operationLock = useRef(false)
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const permission = Boolean(accessToken && data.history !== null && !data.errors.history)
  const finalKnown = !data.loading && !data.errors.active
  const ready = unitsReady(data.units)
  const confirmAllowed = canConfirm({ permission, loading: data.loading, busy, finalKnown,
    otherPending: pendingRevoke !== null, pending: pendingConfirm !== null, active: data.active !== null, unitsReady: ready && data.current !== null && !data.errors.current, complete })
  const revokeAllowed = permission && !data.loading && !busy && finalKnown && !pendingConfirm && Boolean(data.active || pendingRevoke)

  async function handleConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!accessToken || !confirmAllowed || operationLock.current) return
    let attempt = pendingConfirm
    try {
      if (!attempt) {
        attempt = createConfirmationAttempt(note, complete)
        if (!window.confirm('Подтвердить итог всей дисциплины? Результаты и назначения будут защищены от изменения до явного снятия финализации.')) return
      }
    } catch (error) { setActionError(error); return }
    if (!attempt) return
    operationLock.current = true; setBusy(true); setMessage(''); setActionError(null); setPendingConfirm(attempt)
    try {
      const result = await confirmFinal(eventId, disciplineId, attempt, accessToken)
      if (!alive.current) return
      setPendingConfirm(null); setComplete(false); setNote('')
      setMessage(result.status === 'CONFIRMED' ? `Сервер подтвердил итог: ревизия № ${result.revision}.` :
        `Этот запрос относится к ревизии № ${result.revision}, финализация которой уже снята. Новый итог не создан.`)
      setView(result.status === 'CONFIRMED' ? 'final' : 'current'); refresh()
    } catch (error) {
      if (!alive.current) return
      setActionError(error)
      if (!isUncertainFailure(error)) setPendingConfirm(null)
      refresh()
    } finally { operationLock.current = false; if (alive.current) setBusy(false) }
  }
  async function handleRevoke(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!accessToken || !revokeAllowed || operationLock.current) return
    let attempt = pendingRevoke
    try {
      if (!attempt) {
        if (!data.active) return
        attempt = createRevocationAttempt(data.active, reason)
        if (!window.confirm(`Снять финализацию ревизии № ${attempt.revision}? Причина: ${attempt.reason}`)) return
      }
    } catch (error) { setActionError(error); return }
    if (!attempt) return
    operationLock.current = true; setBusy(true); setMessage(''); setActionError(null); setPendingRevoke(attempt)
    try {
      const result = await revokeFinal(eventId, disciplineId, attempt.id, attempt.reason, accessToken)
      if (!alive.current) return
      if (result.status !== 'REVOKED') throw new Error('Сервер не подтвердил снятие. Состояние операции неизвестно; обновите данные.')
      setPendingRevoke(null); setReason(''); setComplete(false)
      setMessage(`Сервер подтвердил снятие ревизии № ${result.revision}. Её снимок сохранён в истории.`)
      setView('current'); refresh()
    } catch (error) {
      if (!alive.current) return
      setActionError(error)
      if (!isUncertainFailure(error)) setPendingRevoke(null)
      refresh()
    } finally { operationLock.current = false; if (alive.current) setBusy(false) }
  }
  const historical = data.history?.find(item => item.id === historyId) ?? null
  const snapshot = view === 'history' ? historical : data.active
  return <div>
    <section className={styles.card} aria-busy={data.loading || busy}>
      <h2>Состояние итогового протокола</h2>
      {data.loading ? <p role="status">Обновляем состояние с сервера…</p> : data.errors.active ?
        <><p>Статус финализации неизвестен. Ошибка загрузки не означает отсутствие итога.</p><ErrorDetails error={data.errors.active} /></> :
        data.active ? <SnapshotInfo snapshot={data.active} /> :
          <p>{data.history?.some(item => item.status === 'REVOKED') ? 'Активного итога нет. Предыдущая финализация снята; история сохранена.' : 'Активный подтверждённый итог отсутствует.'}</p>}
      <div className={styles.toolbar}>
        <button type="button" className={styles.button} disabled={busy || data.loading} onClick={refresh}>Обновить данные</button>
        <button type="button" className={styles.button} aria-pressed={view === 'current'} onClick={() => setView('current')}>Текущий зачёт</button>
        <button type="button" className={styles.button} aria-pressed={view === 'final'} onClick={() => setView('final')}>Подтверждённый снимок</button>
      </div>
      <p className={styles.hint}>Текущий зачёт строится только по опубликованным заплывам и сам по себе не подтверждает завершение всей программы.</p>
    </section>
    {message ? <p role="status" className={styles.success}>{message}</p> : null}
    {actionError ? <ErrorDetails error={actionError} /> : null}
    {pendingConfirm || pendingRevoke ? <p className={styles.notice} role="status">
      Исход действия ещё не подтверждён. Данные исходного запроса сохранены в этой открытой странице.
      Повторите тот же запрос соответствующей кнопкой; не создавайте новое действие с другими данными.
    </p> : null}
    {accessToken ? <section className={styles.card}>
      <h2>Управление финализацией</h2>
      {data.errors.history ? <ErrorDetails error={data.errors.history} /> : null}
      {data.errors.units ? <ErrorDetails error={data.errors.units} /> : null}
      {!permission ? <p className={styles.hint}>Действия доступны только после успешной проверки приватного доступа сервером.</p> : null}
      {data.units ? <p>Заплывов: {data.units.length}. Опубликовано: {data.units.filter(unit => unit.status === 'PUBLISHED').length}.</p> : null}
      {!data.active || pendingConfirm ? <form onSubmit={event => void handleConfirm(event)}>
        <fieldset disabled={busy || pendingConfirm !== null || data.loading || !permission || !finalKnown}>
          <legend>Подтвердить итог дисциплины</legend>
          <label className={styles.field}><span>Примечание (до 500 символов)</span>
            <textarea maxLength={500} value={note} onChange={event => setNote(event.target.value)} rows={3} /></label>
          <label className={styles.checkbox}><input type="checkbox" checked={complete} onChange={event => setComplete(event.target.checked)} />
            <span>Подтверждаю: вся программа дисциплины заведена и завершена, все заплывы опубликованы, подтверждённые заявки распределены.</span></label>
        </fieldset>
        {!ready && !pendingConfirm ? <p className={styles.hint}>Для подтверждения нужен хотя бы один заплыв, и все заплывы должны быть опубликованы. Дополнительные проверки выполнит сервер.</p> : null}
        <button type="submit" className={`${styles.button} ${styles.primary}`} disabled={!confirmAllowed}>
          {busy ? 'Выполняем запрос…' : pendingConfirm ? 'Повторить исходный запрос подтверждения' : 'Подтвердить итог дисциплины'}
        </button>
      </form> : null}
      {data.active || pendingRevoke ? <form onSubmit={event => void handleRevoke(event)}>
        <fieldset disabled={busy || pendingRevoke !== null || data.loading || !permission || !finalKnown}>
          <legend>Снять финализацию ревизии № {pendingRevoke?.revision ?? data.active?.revision}</legend>
          <label className={styles.field}><span>Обязательная причина (до 1000 символов)</span>
            <textarea value={reason} maxLength={1000} rows={3} onChange={event => setReason(event.target.value)} /></label>
        </fieldset>
        <button type="submit" className={`${styles.button} ${styles.danger}`} disabled={!revokeAllowed || (!pendingRevoke && !reason.trim())}>
          {busy ? 'Выполняем запрос…' : pendingRevoke ? 'Повторить снятие той же ревизии с той же причиной' : 'Снять финализацию с причиной'}
        </button>
      </form> : null}
    </section> : null}
    {!data.loading ? <section className={styles.card}>
      <h2>{view === 'current' ? 'Текущий зачёт опубликованной части дисциплины' : view === 'history' ? 'Исторический снимок — не текущий итог' : 'Подтверждённый итоговый снимок'}</h2>
      {view === 'current' ? data.errors.current ? <ErrorDetails error={data.errors.current} /> : data.current ?
        <StandingsTable key="current" data={data.current} /> : <p>Зачёт недоступен.</p> :
        snapshot ? <><SnapshotInfo snapshot={snapshot} /><StandingsTable key={snapshot.id} data={snapshot.standings} /></> :
          <p>Сохранённый снимок недоступен. Текущие результаты не подставляются вместо подтверждённого итога.</p>}
    </section> : null}
    {accessToken && data.history ? <section className={styles.card}>
      <h2>История ревизий</h2>
      {data.history.length === 0 ? <p>Подтверждений ещё не было.</p> : <ul className={styles.history}>
        {[...data.history].reverse().map(item => <li key={item.id}><SnapshotInfo snapshot={item} />
          <div className={styles.toolbar}><button type="button" className={styles.button} onClick={() => { setHistoryId(item.id); setView('history') }}>Показать снимок ревизии № {item.revision}</button></div>
        </li>)}
      </ul>}
    </section> : null}
  </div>
}
