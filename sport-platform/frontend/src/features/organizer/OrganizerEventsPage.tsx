import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import { getMyEvents, type OrganizerEvent } from '../../api/events.api'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этого раздела.'
    }
    return error.message
  }
  return 'Не удалось загрузить список событий.'
}

export function OrganizerEventsPage() {
  const { session } = useAuth()
  const [events, setEvents] = useState<OrganizerEvent[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!session) {
      return
    }

    let isMounted = true

    async function loadEvents() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getMyEvents(session.accessToken)
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

    loadEvents()

    return () => {
      isMounted = false
    }
  }, [session])

  return (
    <section className="admin-section">
      <p className="eyebrow">Панель организатора</p>
      <h1>Мои события</h1>

      {isLoading ? <p>Загрузка...</p> : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        events.length === 0 ? (
          <p>У вас пока нет созданных событий.</p>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Название</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>{event.name}</td>
                  <td>{event.status}</td>
                  <td>
                    <NavLink to={`/events/${event.code}`}>Открыть</NavLink>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      ) : null}
    </section>
  )
}