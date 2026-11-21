import { formatVenueDate, venueTimeZoneLabel } from '../../lib/venue-time.mjs'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getPublicEvents, type PublicEvent } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import styles from './EventsPage.module.css'


function getStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
    REGISTRATION_OPEN: 'Регистрация открыта',
    REGISTRATION_CLOSED: 'Регистрация закрыта',
    COMPLETED: 'Завершено',
  }

  return labels[status] ?? status
}

function getStatusClassName(status: string) {
  const classes: Record<string, string> = {
    DRAFT: styles.statusDraft,
    PUBLISHED: styles.statusPublished,
    REGISTRATION_OPEN: styles.statusOpen,
    REGISTRATION_CLOSED: styles.statusClosed,
    COMPLETED: styles.statusCompleted,
  }

  return classes[status] ?? styles.statusPublished
}

export function EventsPage() {
  const [events, setEvents] = useState<PublicEvent[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function loadEvents() {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const response = await getPublicEvents()
      setEvents(response)
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : 'Не удалось загрузить список событий.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadEvents()
  }, [])

  if (isLoading) {
    return <LoadingState message="Загружаем события…" />
  }

  if (errorMessage) {
    return <ErrorState message={errorMessage} onRetry={() => void loadEvents()} />
  }

  return (
    <section className={styles.page}>
      <div className={styles.hero}>
        <p className={styles.eyebrow}>Календарь соревнований</p>
        <h1 className={styles.title}>Выберите событие для участия</h1>
        <p className={styles.subtitle}>
          Публичные соревнования с открытой регистрацией, опубликованной программой
          и доступными дисциплинами.
        </p>
      </div>

      {events.length === 0 ? (
        <div className={styles.emptyState}>
          Сейчас нет опубликованных событий.
        </div>
      ) : (
        <div className={styles.grid}>
          {events.map((event) => (
            <article key={event.id} className={styles.card}>
              <div className={styles.cardHeader}>
                <span
                  className={`${styles.statusBadge} ${getStatusClassName(event.status)}`}
                >
                  <span className={styles.statusDot} aria-hidden="true" />
                  {getStatusLabel(event.status)}
                </span>
              </div>

              <div className={styles.cardBody}>
                <h2 className={styles.cardTitle}>{event.title}</h2>

                <p className={styles.cardDescription}>
                  {event.description?.trim() ||
                    'Подробная информация о соревновании доступна на странице события.'}
                </p>
              </div>

              <dl className={styles.metaList}>
                <div className={styles.metaItem}>
                  <dt>Регистрация</dt>
                  <dd>
                    {formatVenueDate(event.registrationOpenAt, event.venueTimezone)} —{' '}
                    {formatVenueDate(event.registrationCloseAt, event.venueTimezone)}
                  </dd>
                </div>

                <div className={styles.metaItem}>
                  <dt>Дата события</dt>
                  <dd>
                    {formatVenueDate(event.eventStartAt, event.venueTimezone)} — {formatVenueDate(event.eventEndAt, event.venueTimezone)}
                  </dd>
                </div>
              </dl>

              <p>{venueTimeZoneLabel(event.venueTimezone)}</p>
              <div className={styles.cardFooter}>
                <Link
                  className={styles.primaryAction}
                  to={`/events/${event.publicSlug}`}
                >
                  Подробнее
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}