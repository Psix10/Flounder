import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import { getOperatorEvents, getOperatorEvent, getOperatorDisciplines, getOperatorUnits } from '../../api/operator-workspace.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { operatorEventPath, operatorDisciplinePath, operatorJudgePath } from '../../lib/operator-workspace.mjs'
import { getEventStatusLabel } from '../organizer/event-status'
import { getDisciplineStatusLabel } from '../organizer/discipline-status'
import styles from './OperatorWorkspace.module.css'

function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Сессия завершена. Войдите повторно.'
    if (error.status === 403) return 'Нет действующего доступа оператора. Назначение могло быть снято, учётная запись отключена или роль OPERATOR отозвана.'
    if (error.status === 404) return 'Мероприятие или API рабочего места не найдены. Если патч только установлен, проверьте перезапуск backend.'
    return error.message || 'Не удалось загрузить данные.'
  }
  return error instanceof Error ? error.message : 'Не удалось загрузить данные.'
}
function dateLabel(value?: string | null): string {
  if (!value) return 'Не назначено'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}
function useRead<T>(read: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true); setError(null); setData(null)
    void read().then(value => { if (active) setData(value) })
      .catch((failure: unknown) => { if (active) setError(errorText(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [read, revision])
  return { data, loading, error, refresh: () => setRevision(value => value + 1) }
}
function Screen({ title, back, loading, error, refresh, children }: {
  title: string; back?: string; loading: boolean; error: string | null;
  refresh: () => void; children?: ReactNode;
}) {
  return <section className={styles.page} aria-busy={loading}>
    <Link className={styles.back} to={back ?? '/'}>{back ? '← Назад' : '← В публичный каталог'}</Link>
    <p className={styles.eyebrow}>Работа оператора</p><h1>{title}</h1>
    <button className={styles.button} type="button" disabled={loading} onClick={refresh}>Обновить</button>
    {loading ? <p role="status">Загружаем данные и проверяем назначение…</p> : null}
    {error ? <p className={styles.error} role="alert">{error}</p> : null}
    {!loading && !error ? children : null}
  </section>
}
function MissingSession() {
  return <section className={styles.page}><Link to="/login">Войти</Link><p role="alert">Отсутствует сессия или неверно указан маршрут.</p></section>
}
export function OperatorEventsPage() {
  const { session } = useAuth()
  return session?.accessToken ? <EventsContent key={session.expiresAt} token={session.accessToken}/> : <MissingSession/>
}
function EventsContent({ token }: { token: string }) {
  const read = useCallback(() => getOperatorEvents(token), [token])
  const resource = useRead(read)
  return <Screen title="Мои мероприятия оператора" {...resource}>
    <p>Здесь только мероприятия, на которые вы назначены оператором. Список не зависит от их наличия в публичном каталоге.</p>
    {resource.data?.length === 0 ? <div className={styles.card}><h2>Назначений пока нет</h2><p>Организатор должен назначить вас на мероприятие. После назначения обновите список.</p></div> : null}
    <div className={styles.list}>{resource.data?.map(event => <article className={styles.card} key={event.id}>
      <span className={styles.badge}>{getEventStatusLabel(event.status)}</span>
      <h2>{event.title}</h2><p>Начало: {dateLabel(event.eventStartAt)}</p><p>Окончание: {dateLabel(event.eventEndAt)}</p>
      <Link className={styles.link} to={operatorEventPath(event.id)}>Открыть дисциплины</Link>
    </article>)}</div>
  </Screen>
}
export function OperatorEventPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const { session } = useAuth()
  return eventId && session?.accessToken
    ? <EventContent key={`${eventId}:${session.expiresAt}`} eventId={eventId} token={session.accessToken}/>
    : <MissingSession/>
}
function EventContent({ eventId, token }: { eventId: string; token: string }) {
  const read = useCallback(async () => {
    const [event, disciplines] = await Promise.all([getOperatorEvent(eventId, token), getOperatorDisciplines(eventId, token)])
    return { event, disciplines }
  }, [eventId, token])
  const resource = useRead(read)
  return <Screen title={resource.data?.event.title ?? 'Дисциплины мероприятия'} back="/operator/events" {...resource}>
    {resource.data ? <>
      <p><span className={styles.badge}>{getEventStatusLabel(resource.data.event.status)}</span> · Начало: {dateLabel(resource.data.event.eventStartAt)}</p>
      <h2>Дисциплины · {resource.data.disciplines.length}</h2>
      <p>Выберите дисциплину, затем заплыв. Этот список не является расписанием стартов.</p>
      {!resource.data.disciplines.length ? <p className={styles.card}>Дисциплины пока не созданы.</p> : null}
      <div className={styles.list}>{resource.data.disciplines.map(discipline => <article className={styles.card} key={discipline.id}>
        <span className={styles.badge}>{getDisciplineStatusLabel(discipline.status)}</span>
        <h3>{formatHeatLabel(discipline.name)}</h3>
        <p>Формат: {discipline.competitionFormat} · Тип: {discipline.unitType ?? '—'} · Результат: {discipline.resultType ?? '—'}</p>
        <Link className={styles.link} to={operatorDisciplinePath(eventId, discipline.id)}>Открыть заплывы</Link>
      </article>)}</div>
    </> : null}
  </Screen>
}
export function OperatorDisciplinePage() {
  const { eventId, disciplineId } = useParams<{ eventId: string; disciplineId: string }>()
  const { session } = useAuth()
  return eventId && disciplineId && session?.accessToken
    ? <DisciplineContent key={`${eventId}:${disciplineId}:${session.expiresAt}`} eventId={eventId} disciplineId={disciplineId} token={session.accessToken}/>
    : <MissingSession/>
}
function DisciplineContent({ eventId, disciplineId, token }: { eventId: string; disciplineId: string; token: string }) {
  const read = useCallback(async () => {
    const [event, disciplines] = await Promise.all([getOperatorEvent(eventId, token), getOperatorDisciplines(eventId, token)])
    const discipline = disciplines.find(item => item.id === disciplineId)
    if (!discipline) throw new Error('Дисциплина не принадлежит выбранному мероприятию или недоступна.')
    const units = await getOperatorUnits(disciplineId, token)
    return { event, discipline, units: [...units].sort((a, b) =>
      (a.sequenceNumber ?? Number.MAX_SAFE_INTEGER) - (b.sequenceNumber ?? Number.MAX_SAFE_INTEGER) || a.id.localeCompare(b.id)) }
  }, [eventId, disciplineId, token])
  const resource = useRead(read)
  return <Screen title={resource.data ? formatHeatLabel(resource.data.discipline.name) : 'Заплывы дисциплины'}
    back={`/operator/events/${encodeURIComponent(eventId)}`} {...resource}>
    {resource.data ? <>
      <p>{resource.data.event.title}</p><p>Заплывов: {resource.data.units.length}</p>
      <p>Открытие судейской панели не отменяет публикацию результатов и защиту подтверждённого итога.</p>
      {!resource.data.units.length ? <div className={styles.card}><h2>Заплывов пока нет</h2><p>Организатор должен сформировать стартовый протокол.</p></div> : null}
      <div className={styles.list}>{resource.data.units.map(unit => <article className={styles.card} key={unit.id}>
        <span className={styles.badge}>{unit.status === 'PUBLISHED' ? 'Опубликовано' : unit.status === 'DRAFT' ? 'Черновик' : unit.status}</span>
        <h3>{formatHeatLabel(unit.label)}</h3><p>Старт: {dateLabel(unit.scheduledAt)}</p>
        <Link className={styles.link} to={operatorJudgePath(eventId, disciplineId, unit.id)}>Открыть судейскую панель</Link>
      </article>)}</div>
    </> : null}
  </Screen>
}
