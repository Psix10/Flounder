import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getRegistrations,
  type Registration,
} from '../../api/registrations.api'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этого раздела.'
    }

    return error.message
  }

  return 'Не удалось загрузить список заявок.'
}

function registrationStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    CANCELLED: 'Отменена',
  }

  return labels[status] ?? status
}

function shortId(value: string) {
  return value.slice(0, 8)
}

export function OperatorPaymentsPage() {
  const { session } = useAuth()
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!session?.accessToken) {
      setIsLoading(false)
      return
    }

    let isMounted = true

    async function loadRegistrations() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getRegistrations(session.accessToken)

        if (isMounted) {
          setRegistrations(data)
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

    loadRegistrations()

    return () => {
      isMounted = false
    }
  }, [session?.accessToken])

  return (
    <section className="admin-section">
      <p className="eyebrow">Панель оператора</p>
      <h1>Заявки и платежи</h1>

      {isLoading ? <p>Загрузка...</p> : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        registrations.length === 0 ? (
          <p>Заявок пока нет.</p>
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th scope="col">Заявка</th>
                  <th scope="col">Событие</th>
                  <th scope="col">Статус</th>
                  <th scope="col">
                    <span className="visually-hidden">Действия</span>
                  </th>
                </tr>
              </thead>

              <tbody>
                {registrations.map((registration) => (
                  <tr key={registration.id}>
                    <td>
                      <strong className="registration-title">
                        Заявка #{shortId(registration.id)}
                      </strong>

                      <span className="registration-id">
                        {registration.id}
                      </span>
                    </td>

                    <td>
                      <strong className="registration-event-name">
                        {registration.eventName ?? 'Событие'}
                      </strong>

                      {!registration.eventName ? (
                        <span className="registration-id">
                          {registration.eventId}
                        </span>
                      ) : null}
                    </td>

                    <td>
                      <span
                        className={`status-badge status-${registration.status.toLowerCase()}`}
                      >
                        {registrationStatusLabel(registration.status)}
                      </span>
                    </td>

                    <td className="admin-table-action">
                      <NavLink
                        className="button button-secondary"
                        to={`/operator/registrations/${registration.id}`}
                      >
                        Открыть
                      </NavLink>
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