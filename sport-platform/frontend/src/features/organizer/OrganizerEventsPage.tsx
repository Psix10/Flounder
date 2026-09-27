import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { getMyEvents, type OrganizerEvent } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './OrganizerEventsPage.module.css'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этого раздела.'
    }

    return error.message
  }

  return 'Не удалось загрузить список событий.'
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
    REGISTRATION_OPEN: 'Регистрация открыта',
    REGISTRATION_CLOSED: 'Регистрация закрыта',
    FINISHED: 'Завершено',
    CANCELLED: 'Отменено',
  }

  return labels[status] ?? status
}

function statusClassName(status: string) {
  const classes: Record<string, string> = {
    DRAFT: styles.statusDraft,
    PUBLISHED: styles.statusPublished,
    REGISTRATION_OPEN: styles.statusOpen,
    REGISTRATION_CLOSED: styles.statusClosed,
    FINISHED: styles.statusFinished,
    CANCELLED: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status] ?? styles.statusNeutral}`
}

export function OrganizerEventsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [events, setEvents] = useState<OrganizerEvent[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadEvents() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getMyEvents(authenticatedAccessToken)

        if (isMounted) {
          setEvents(data)
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadEvents()

    return () => {
      isMounted = false
    }
  }, [accessToken])

  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>Панель организатора</p>

      <h1 className={styles.title}>Мои события</h1>

      {isLoading ? (
        <div className={styles.stateCard}>
          Загружаем список событий…
        </div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        events.length === 0 ? (
          <div className={styles.stateCard}>
            У вас пока нет созданных событий.
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Событие</th>
                  <th scope="col">Статус</th>
                  <th scope="col">
                    <span className={styles.visuallyHidden}>Действия</span>
                  </th>
                </tr>
              </thead>

              <tbody>
                {events.map((event) => (
                  <tr key={event.id}>
                    <td>
                      <strong className={styles.eventName}>
                        {event.title}
                      </strong>

                      <span className={styles.eventCode}>
                        {event.publicSlug}
                      </span>
                    </td>

                    <td>
                      <span className={statusClassName(event.status)}>
                        {statusLabel(event.status)}
                      </span>
                    </td>

                    <td>
                      <div className={styles.actions}>
                        <NavLink
                          className={styles.secondaryLink}
                          to={`/organizer/events/${event.id}/registrations`}
                        >
                          Заявки
                        </NavLink>

                        <NavLink
                          className={styles.openLink}
                          to={`/events/${event.publicSlug}`}
                        >
                          Открыть
                        </NavLink>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}
    </section>
  )
}