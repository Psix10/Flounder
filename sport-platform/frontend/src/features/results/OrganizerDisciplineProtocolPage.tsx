import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getEventById, getEventDisciplines, type Event, type EventDiscipline } from '../../api/events.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { DisciplineProtocolBoard } from './DisciplineProtocolBoard'
import { protocolErrorMessage } from './protocol-model.mjs'
import styles from './ProtocolBoard.module.css'

function ProtocolPageContent({ eventId, disciplineId, accessToken }: { eventId: string; disciplineId: string; accessToken: string }) {
  const [data, setData] = useState<{ event: Event; discipline: EventDiscipline } | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [epoch, setEpoch] = useState(0)
  useEffect(() => {
    let valid = true
    setLoading(true); setData(null); setError(null)
    void Promise.all([getEventById(eventId, accessToken), getEventDisciplines(eventId, accessToken)])
      .then(([event, disciplines]) => {
        if (!valid) return
        const discipline = disciplines.find(item => item.id === disciplineId)
        if (!discipline) throw new Error('Дисциплина не найдена в этом мероприятии.')
        setData({ event, discipline })
      })
      .catch(failure => { if (valid) setError(failure) })
      .finally(() => { if (valid) setLoading(false) })
    return () => { valid = false }
  }, [eventId, disciplineId, accessToken, epoch])
  return <section className={styles.page}>
    <Link className={styles.link} to={`/organizer/events/${encodeURIComponent(eventId)}`}>← К мероприятию</Link>
    <h1>Зачёт и итоговый протокол</h1>
    {loading ? <p role="status">Загружаем дисциплину…</p> : null}
    {error ? <div className={styles.error} role="alert"><p>{protocolErrorMessage(error)}</p>
      <button type="button" className={styles.button} onClick={() => setEpoch(value => value + 1)}>Повторить загрузку</button></div> : null}
    {!loading && !error && data ? <>
      <p>{data.event.title} — {data.discipline.name}</p>
      <div className={styles.toolbar}>
        {data.event.publicSlug ? <Link className={styles.link} to={`/events/${encodeURIComponent(data.event.publicSlug)}/disciplines/${encodeURIComponent(disciplineId)}/standings`}>Публичный зачёт</Link> : null}
      </div>
      <DisciplineProtocolBoard eventId={eventId} disciplineId={disciplineId} accessToken={accessToken} />
    </> : null}
  </section>
}
export function OrganizerDisciplineProtocolPage() {
  const { eventId, disciplineId } = useParams<{ eventId: string; disciplineId: string }>()
  const { session } = useAuth()
  if (!eventId || !disciplineId || !session?.accessToken) return <p role="alert">Не указана дисциплина или отсутствует авторизованная сессия.</p>
  return <ProtocolPageContent key={`${eventId}:${disciplineId}:${session.expiresAt}`} eventId={eventId} disciplineId={disciplineId} accessToken={session.accessToken} />
}
