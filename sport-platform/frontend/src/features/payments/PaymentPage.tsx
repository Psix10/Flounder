import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getRegistration,
  type Registration,
} from '../../api/registrations.api'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этой страницы.'
    }
    return error.message
  }
  return 'Не удалось загрузить данные заявки.'
}

export function PaymentPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { registrationId } = useParams()
  const [searchParams] = useSearchParams()

  const [registration, setRegistration] = useState<Registration | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const paymentStatus = searchParams.get('status')

  useEffect(() => {
    if (!session) {
      navigate('/login', { replace: true })
      return
    }

    if (!registrationId) {
      setErrorMessage('Идентификатор заявки не указан.')
      setIsLoading(false)
      return
    }

    let isMounted = true

    async function loadRegistration() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getRegistration(registrationId)
        if (isMounted) {
          setRegistration(data)
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

    loadRegistration()

    return () => {
      isMounted = false
    }
  }, [session, registrationId, navigate])

  return (
    <section className="admin-section">
      <p className="eyebrow">Оплата участия</p>
      <h1>Заявка и статус оплаты</h1>

      {isLoading ? <p>Загрузка...</p> : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && registration ? (
        <>
          <dl className="definition-list">
            <div>
              <dt>ID заявки</dt>
              <dd>{registration.id}</dd>
            </div>
            <div>
              <dt>Событие</dt>
              <dd>{registration.eventName ?? registration.eventId}</dd>
            </div>
            <div>
              <dt>Статус заявки</dt>
              <dd>{registration.status}</dd>
            </div>
          </dl>

          {paymentStatus === 'success' ? (
            <div className="success-banner" role="status">
              Оплата успешно завершена. Спасибо за участие!
            </div>
          ) : paymentStatus === 'failed' ? (
            <div className="form-error" role="alert">
              Оплата не удалась. Попробуйте ещё раз или свяжитесь с организатором.
            </div>
          ) : (
            <p>
              Вы будете перенаправлены на платёжную страницу. После завершения
              оплаты сюда вернётся статус операции.
            </p>
          )}
        </>
      ) : null}
    </section>
  )
}