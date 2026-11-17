import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { getEventById } from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  assignEventOperator, findEventOperator, getEventOperatorMembers, removeEventOperator,
  type EventOperatorCandidate, type EventOperatorMember,
} from '../../api/event-operators.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { changeAndReadOperators, normalizeOperatorEmail } from '../../lib/event-operators.mjs'
import styles from './OrganizerEventOperatorsPage.module.css'

function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Сессия завершена. Войдите повторно.'
    if (error.status === 403) return 'Нет права управлять операторами этого мероприятия.'
    if (error.status === 404) return 'Мероприятие или API операторов не найдены. Если патч только установлен, проверьте перезапуск backend.'
    if (error.code === 'events.operator_not_eligible') return 'Пользователь больше не является активным оператором. Обновите список и повторите поиск.'
    return error.message || 'Не удалось выполнить операцию.'
  }
  return error instanceof Error ? error.message : 'Не удалось выполнить операцию.'
}
function dateLabel(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}
export function OrganizerEventOperatorsPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const { session } = useAuth()
  if (!eventId || !session?.accessToken) return <section className={styles.page}>
    <Link to="/organizer/events">← К мероприятиям</Link>
    <p role="alert">Не указан идентификатор мероприятия или отсутствует сессия.</p>
  </section>
  return <OperatorsContent key={`${eventId}:${session.expiresAt}`} eventId={eventId} token={session.accessToken}/>
}
function OperatorsContent({ eventId, token }: { eventId: string; token: string }) {
  const [title, setTitle] = useState('')
  const [members, setMembers] = useState<EventOperatorMember[]>([])
  const [ready, setReady] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [needsSync, setNeedsSync] = useState(true)
  const [email, setEmail] = useState('')
  const [candidate, setCandidate] = useState<EventOperatorCandidate | null>(null)
  const [searched, setSearched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const mounted = useRef(false)
  const locked = useRef(false)

  useEffect(() => {
    let active = true
    mounted.current = true
    locked.current = true
    async function load() {
      try {
        const [event, rows] = await Promise.all([getEventById(eventId, token), getEventOperatorMembers(eventId, token)])
        if (event?.id !== eventId) throw new Error('Ответ относится к другому мероприятию.')
        if (!active) return
        setTitle(event.title); setMembers(rows); setReady(true); setNeedsSync(false)
      } catch (failure) {
        if (active) { setError(errorText(failure)); setNeedsSync(true) }
      } finally {
        if (active) { locked.current = false; setLoading(false) }
      }
    }
    void load()
    return () => { active = false; mounted.current = false }
  }, [eventId, token])

  function acquire(allowSync = false): boolean {
    if (!mounted.current || locked.current || loading || (!allowSync && (!ready || needsSync))) return false
    locked.current = true; setBusy(true); setError(null); setMessage(null)
    return true
  }
  function release() {
    locked.current = false
    if (mounted.current) setBusy(false)
  }
  async function refresh() {
    if (!acquire(true)) return
    setCandidate(null); setSearched(false)
    try {
      const [event, rows] = await Promise.all([getEventById(eventId, token), getEventOperatorMembers(eventId, token)])
      if (event?.id !== eventId) throw new Error('Ответ относится к другому мероприятию.')
      if (!mounted.current) return
      setTitle(event.title); setMembers(rows); setReady(true); setNeedsSync(false)
      setMessage('Список операторов сверен с сервером.')
    } catch (failure) {
      if (mounted.current) { setError(errorText(failure)); setNeedsSync(true) }
    } finally { release() }
  }
  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    let normalized: string
    try { normalized = normalizeOperatorEmail(email) }
    catch (failure) { setError(errorText(failure)); return }
    if (!acquire()) return
    setCandidate(null); setSearched(false)
    try {
      const [found, rows] = await Promise.all([findEventOperator(eventId, normalized, token), getEventOperatorMembers(eventId, token)])
      if (!mounted.current) return
      setMembers(rows); setCandidate(found[0] ?? null); setSearched(true)
    } catch (failure) {
      if (mounted.current) { setError(errorText(failure)); setNeedsSync(true) }
    } finally { release() }
  }
  async function change(userId: string, displayName: string, userEmail: string | null, assigned: boolean) {
    if (!mounted.current || locked.current || loading || !ready || needsSync) return
    const target = userEmail ? `${displayName} (${userEmail})` : `${displayName} · ID ${userId}`
    if (!window.confirm(assigned
      ? `Назначить оператора ${target} на мероприятие «${title}»? Глобальные роли пользователя не изменятся.`
      : `Снять назначение оператора ${target} с мероприятия «${title}»? Глобальная роль и назначения на другие мероприятия сохранятся.`)) return
    if (!acquire()) return
    setCandidate(null); setSearched(false)
    try {
      const rows = await changeAndReadOperators(
        () => assigned ? assignEventOperator(eventId, userId, token) : removeEventOperator(eventId, userId, token),
        () => getEventOperatorMembers(eventId, token), eventId, userId, assigned,
      )
      if (!mounted.current) return
      setMembers(rows); setNeedsSync(false)
      setMessage(assigned ? 'Оператор назначен. Назначение подтверждено чтением списка.' : 'Назначение снято. Отсутствие назначения подтверждено чтением списка.')
    } catch (failure) {
      if (mounted.current) { setError(errorText(failure)); setNeedsSync(true) }
    } finally { release() }
  }
  const blocked = busy || loading || !ready || needsSync
  const alreadyAssigned = candidate ? candidate.assigned || members.some(row => row.operatorUserId === candidate.userId) : false
  return <section className={styles.page} aria-busy={busy || loading}>
    <Link className={styles.back} to={`/organizer/events/${encodeURIComponent(eventId)}`}>← К мероприятию</Link>
    <h1>Операторы мероприятия</h1>
    {title ? <p className={styles.subtitle}>{title}</p> : null}
    <p>Назначение даёт оператору доступ только к выбранному мероприятию. Глобальные роли здесь не выдаются.</p>
    <button className={styles.button} type="button" disabled={busy || loading} onClick={() => void refresh()}>Сверить с сервером</button>
    {loading ? <p role="status">Загружаем мероприятие и проверяем доступ к назначениям…</p> : null}
    {error ? <p className={styles.error} role="alert">{error}</p> : null}
    {message ? <p className={styles.success} role="status">{message}</p> : null}
    {!loading && needsSync ? <p className={styles.notice} role="status">
      Актуальность списка не подтверждена. Изменения заблокированы до успешной сверки.
      После ошибки изменяющий запрос мог выполниться на сервере. Автоматического повтора нет.
    </p> : null}
    <section className={styles.card}>
      <h2>Назначить оператора</h2>
      <p>Введите точный email существующего активного пользователя с ролью OPERATOR. Если роли нет, её отдельно назначает администратор платформы.</p>
      <form className={styles.search} onSubmit={event => void search(event)}>
        <label className={styles.field}><span>Email оператора</span>
          <input type="email" required maxLength={254} autoComplete="off" value={email} disabled={busy || loading}
            onChange={event => { setEmail(event.target.value); setCandidate(null); setSearched(false); setError(null); setMessage(null) }}/>
        </label>
        <button className={styles.button} type="submit" disabled={blocked || !email.trim()}>Найти оператора</button>
      </form>
      {searched && !candidate ? <p role="status">Активный оператор с таким email не найден. Проверьте адрес; при неоднозначном совпадении назначение также недоступно.</p> : null}
      {candidate ? <div className={styles.member}>
        <div><h3>{candidate.displayName}</h3><p>{candidate.email}</p><small>ID: {candidate.userId}</small></div>
        <button className={styles.button} type="button" disabled={blocked || alreadyAssigned}
          onClick={() => void change(candidate.userId, candidate.displayName, candidate.email, true)}>
          {alreadyAssigned ? 'Уже назначен' : 'Назначить на мероприятие'}
        </button>
      </div> : null}
    </section>
    <section className={styles.card}>
      <h2>Назначенные операторы{ready ? ` · ${members.length}` : ''}</h2>
      {ready && !members.length ? <p>Операторы пока не назначены.</p> : null}
      {members.map(row => <article className={styles.member} key={row.id}>
        <div><h3>{row.displayName}</h3>{row.email ? <p>{row.email}</p> : null}
          <p>Назначен: {dateLabel(row.createdAt)}</p>
          <p className={row.eligible ? styles.active : styles.warning}>{row.eligible ? 'Активный оператор' : 'Неактивен, недоступен или глобальная роль OPERATOR отозвана'}</p>
          <details><summary>Технические сведения</summary><p>ID пользователя: <code>{row.operatorUserId}</code></p><p>Назначил: <code>{row.assignedByUserId}</code></p></details>
        </div>
        <button className={`${styles.button} ${styles.danger}`} type="button" disabled={blocked}
          onClick={() => void change(row.operatorUserId, row.displayName, row.email, false)}>Снять назначение</button>
      </article>)}
    </section>
  </section>
}
