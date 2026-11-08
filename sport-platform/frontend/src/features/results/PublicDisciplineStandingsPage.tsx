import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getPublicEventDetails, type PublicEventDetails } from '../../api/events.api'
import { DisciplineProtocolBoard } from './DisciplineProtocolBoard'
import { protocolErrorMessage } from './protocol-model.mjs'
import styles from './ProtocolBoard.module.css'

function PublicPageContent({ publicSlug, disciplineId }: { publicSlug: string; disciplineId: string }) {
  const [event, setEvent] = useState<PublicEventDetails | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [epoch, setEpoch] = useState(0)
  useEffect(() => {
    let valid = true
    setLoading(true); setEvent(null); setError(null)
    void getPublicEventDetails(publicSlug).then(value => {
      if (!valid) return
      if (!value.disciplines?.some(item => item.id === disciplineId)) throw new Error('Дисциплина не найдена на публичной странице события.')
      setEvent(value)
    }).catch(failure => { if (valid) setError(failure) }).finally(() => { if (valid) setLoading(false) })
    return () => { valid = false }
  }, [publicSlug, disciplineId, epoch])
  return <section className={styles.page}>
    <div className={styles.toolbar}>
      <Link className={styles.link} to={`/events/${encodeURIComponent(publicSlug)}`}>← К событию</Link>
      <Link className={styles.link} to={`/events/${encodeURIComponent(publicSlug)}/disciplines/${encodeURIComponent(disciplineId)}/results`}>Результаты по заплывам</Link>
    </div>
    <h1>Общий и категорийный зачёт</h1>
    {loading ? <p role="status">Загружаем событие…</p> : null}
    {error ? <div className={styles.error} role="alert"><p>{protocolErrorMessage(error)}</p>
      <button type="button" className={styles.button} onClick={() => setEpoch(value => value + 1)}>Повторить загрузку</button></div> : null}
    {!loading && !error && event ? <>
      <p>{event.title} — {event.disciplines.find(item => item.id === disciplineId)?.name}</p>
      <DisciplineProtocolBoard eventId={event.id} disciplineId={disciplineId} />
    </> : null}
  </section>
}
export function PublicDisciplineStandingsPage() {
  const { publicSlug, disciplineId } = useParams<{ publicSlug: string; disciplineId: string }>()
  if (!publicSlug || !disciplineId) return <p role="alert">Не указаны событие или дисциплина.</p>
  return <PublicPageContent key={`${publicSlug}:${disciplineId}`} publicSlug={publicSlug} disciplineId={disciplineId} />
}
