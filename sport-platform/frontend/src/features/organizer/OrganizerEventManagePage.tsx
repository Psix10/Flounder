import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router'

import {
  closeEventRegistration,
  completeEvent,
  getEventById,
  getEventDisciplines,
  openEventRegistration,
  publishEvent,
  publishEventDiscipline,
  type Event,
  type EventDiscipline,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import {
  getDisciplineStatusLabel,
  getDisciplineStatusTone,
} from './discipline-status'
import {
  getEventStatusLabel,
  getEventStatusTone,
  normalizeEventStatus,
} from './event-status'
import styles from './OrganizerEventManagePage.module.css'

type EventAction =
  | 'publish'
  | 'open-registration'
  | 'close-registration'
  | 'complete'

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к управлению мероприятием.'
    }

    return error.message || fallback
  }

  return fallback
}

function statusClassName(status: string): string {
  switch (getEventStatusTone(status)) {
    case 'draft':
      return `${styles.statusBadge} ${styles.statusDraft}`
    case 'published':
      return `${styles.statusBadge} ${styles.statusPublished}`
    case 'open':
      return `${styles.statusBadge} ${styles.statusOpen}`
    case 'closed':
      return `${styles.statusBadge} ${styles.statusClosed}`
    case 'live':
      return `${styles.statusBadge} ${styles.statusLive}`
    case 'completed':
      return `${styles.statusBadge} ${styles.statusCompleted}`
    case 'archived':
      return `${styles.statusBadge} ${styles.statusArchived}`
    default:
      return `${styles.statusBadge} ${styles.statusNeutral}`
  }
}

function disciplineStatusClassName(status?: string | null): string {
  switch (getDisciplineStatusTone(status)) {
    case 'draft':
      return `${styles.statusBadge} ${styles.statusDraft}`
    case 'published':
      return `${styles.statusBadge} ${styles.statusPublished}`
    case 'archived':
      return `${styles.statusBadge} ${styles.statusArchived}`
    default:
      return `${styles.statusBadge} ${styles.statusNeutral}`
  }
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return '—'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function OrganizerEventManagePage() {
  const { eventId } = useParams()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [event, setEvent] = useState<Event | null>(null)
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [publishingId, setPublishingId] = useState<string | null>(null)
  const [eventAction, setEventAction] = useState<EventAction | null>(null)

  useEffect(() => {
    if (!eventId || !accessToken) {
      setIsLoading(false)
      return
    }

    let isMounted = true

    async function loadData() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const [eventData, disciplinesData] = await Promise.all([
          getEventById(eventId, accessToken),
          getEventDisciplines(eventId, accessToken),
        ])

        if (!isMounted) {
          return
        }

        setEvent(eventData)
        setDisciplines(disciplinesData)
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(error, 'Не удалось загрузить мероприятие.'),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadData()

    return () => {
      isMounted = false
    }
  }, [eventId, accessToken])

  async function handlePublishDiscipline(disciplineId: string) {
    if (!eventId || !accessToken) {
      return
    }

    setPublishingId(disciplineId)
    setErrorMessage(null)
    setActionMessage(null)

    try {
      const updatedDiscipline = await publishEventDiscipline(
        eventId,
        disciplineId,
        accessToken,
      )

      setDisciplines((current) =>
        current.map((discipline) =>
          discipline.id === disciplineId ? updatedDiscipline : discipline,
        ),
      )

      setActionMessage('Дисциплина опубликована.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось опубликовать дисциплину.'),
      )
    } finally {
      setPublishingId(null)
    }
  }

  async function handleEventAction(action: EventAction) {
    if (!eventId || !accessToken || !event) {
      return
    }

    setEventAction(action)
    setErrorMessage(null)
    setActionMessage(null)

    try {
      let updatedEvent: Event
      let successMessage: string

      switch (action) {
        case 'publish':
          updatedEvent = await publishEvent(eventId, accessToken)
          successMessage = 'Мероприятие опубликовано.'
          break

        case 'open-registration':
          updatedEvent = await openEventRegistration(eventId, accessToken)
          successMessage = 'Регистрация открыта.'
          break

        case 'close-registration':
          updatedEvent = await closeEventRegistration(eventId, accessToken)
          successMessage = 'Регистрация закрыта.'
          break

        case 'complete':
          updatedEvent = await completeEvent(eventId, accessToken)
          successMessage = 'Мероприятие завершено.'
          break
      }

      setEvent(updatedEvent)
      setActionMessage(successMessage)
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось изменить статус мероприятия.'),
      )
    } finally {
      setEventAction(null)
    }
  }

  const eventStatus = normalizeEventStatus(event?.status)

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/organizer/events">
        ← К мероприятиям
      </NavLink>

      {isLoading ? (
        <div className={styles.stateCard}>Загружаем мероприятие…</div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {actionMessage ? (
        <p className={styles.successMessage} role="status">
          {actionMessage}
        </p>
      ) : null}

      {!isLoading && event ? (
        <>
          <header className={styles.hero}>
            <div>
              <p className={styles.eyebrow}>Управление мероприятием</p>
              <h1 className={styles.title}>{event.title}</h1>

              <div className={styles.meta}>
                <span className={statusClassName(event.status)}>
                  {getEventStatusLabel(event.status)}
                </span>
                <span>Начало: {formatDate(event.eventStartAt)}</span>
                <span>Окончание: {formatDate(event.eventEndAt)}</span>
              </div>

              {event.description ? (
                <p className={styles.description}>{event.description}</p>
              ) : null}
            </div>

            <div className={styles.heroActions}>
              <NavLink
                className={styles.secondaryLink}
                to={`/organizer/events/${encodeURIComponent(
                  event.id,
                )}/registrations`}
              >
                Заявки
              </NavLink>

              {event.publicSlug ? (
                <NavLink
                  className={styles.secondaryLink}
                  to={`/events/${encodeURIComponent(event.publicSlug)}`}
                >
                  Открыть страницу
                </NavLink>
              ) : null}
            </div>
          </header>

          <section className={styles.card}>
            <h2 className={styles.cardTitle}>Статус мероприятия</h2>

            <div className={styles.actionRow}>
              {eventStatus === 'DRAFT' ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={eventAction === 'publish'}
                  onClick={() => void handleEventAction('publish')}
                >
                  {eventAction === 'publish'
                    ? 'Публикуем…'
                    : 'Опубликовать мероприятие'}
                </button>
              ) : null}

              {eventStatus === 'PUBLISHED' ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={eventAction === 'open-registration'}
                  onClick={() =>
                    void handleEventAction('open-registration')
                  }
                >
                  {eventAction === 'open-registration'
                    ? 'Открываем…'
                    : 'Открыть регистрацию'}
                </button>
              ) : null}

              {eventStatus === 'REGISTRATION_OPEN' ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={eventAction === 'close-registration'}
                  onClick={() =>
                    void handleEventAction('close-registration')
                  }
                >
                  {eventAction === 'close-registration'
                    ? 'Закрываем…'
                    : 'Закрыть регистрацию'}
                </button>
              ) : null}

              {eventStatus === 'REGISTRATION_CLOSED' ||
              eventStatus === 'LIVE' ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={eventAction === 'complete'}
                  onClick={() => void handleEventAction('complete')}
                >
                  {eventAction === 'complete'
                    ? 'Завершаем…'
                    : 'Завершить мероприятие'}
                </button>
              ) : null}
            </div>
          </section>

          <section className={styles.card}>
            <h2 className={styles.cardTitle}>Дисциплины</h2>

            {disciplines.length === 0 ? (
              <div className={styles.stateCard}>
                Для этого мероприятия пока нет дисциплин.
              </div>
            ) : (
              <div className={styles.disciplineList}>
                {disciplines.map((discipline) => {
                  const disciplineStatus = discipline.status || 'UNKNOWN'

                  return (
                    <article
                      key={discipline.id}
                      className={styles.disciplineCard}
                    >
                      <div className={styles.disciplineHeader}>
                        <div>
                          <h3 className={styles.disciplineTitle}>
                            {discipline.name}
                          </h3>
                          <p className={styles.disciplineMeta}>
                            Код: {discipline.code || '—'} · Формат:{' '}
                            {discipline.competitionFormat}
                          </p>
                        </div>

                        <span
                          className={disciplineStatusClassName(
                            disciplineStatus,
                          )}
                        >
                          {getDisciplineStatusLabel(disciplineStatus)}
                        </span>
                      </div>

                      <div className={styles.disciplineFooter}>
                        <span>
                          Лимит участников:{' '}
                          {discipline.participantLimit ?? '—'}
                        </span>

                        {disciplineStatus === 'DRAFT' ? (
                          <button
                            type="button"
                            className={styles.primaryButton}
                            disabled={publishingId === discipline.id}
                            onClick={() =>
                              void handlePublishDiscipline(discipline.id)
                            }
                          >
                            {publishingId === discipline.id
                              ? 'Публикуем…'
                              : 'Опубликовать'}
                          </button>
                        ) : null}
                      </div>
                    </article>
                  )
                })}
              </div>
            )}
          </section>
        </>
      ) : null}
    </section>
  )
}