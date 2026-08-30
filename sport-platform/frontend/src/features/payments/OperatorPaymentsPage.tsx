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

export function OperatorPaymentsPage() {
  const { session } = useAuth()
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!session) {
      return
    }

    let isMounted = true

    async function loadRegistrations() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getRegistrations()
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
  }, [session])

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
          <table className="admin-table">
            <thead>
              <tr>
                <th>ID заявки</th>
                <th>Событие</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {registrations.map((registration) => (
                <tr key={registration.id}>
                  <td>{registration.id}</td>
                  <td>{registration.eventName ?? registration.eventId}</td>
                  <td>{registration.status}</td>
                  <td>
                    <NavLink to={`/my/registrations/${registration.id}/payment`}>
                      Открыть оплату
                    </NavLink>
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