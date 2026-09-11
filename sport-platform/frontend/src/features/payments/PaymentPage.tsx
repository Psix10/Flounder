import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getMyRegistrations,
  type Registration,
} from '../../api/registrations.api'
import {
  createPaymentForRegistration,
  getPaymentForRegistration,
  type Payment,
} from '../../api/payments.api'

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для выполнения этого действия.'
    }

    if (error.status === 404) {
      return 'Запись не найдена.'
    }

    return error.message
  }

  return fallback
}

function paymentStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает оплаты',
    WAITING_FOR_CAPTURE: 'Ожидает подтверждения',
    SUCCEEDED: 'Оплата подтверждена',
    CANCELED: 'Оплата отменена',
    FAILED: 'Ошибка оплаты',
  }

  return labels[status] ?? status
}

export function PaymentPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { registrationId } = useParams<{ registrationId: string }>()
  const [searchParams] = useSearchParams()
  const accessToken = session?.accessToken ?? null

  const [registration, setRegistration] = useState<Registration | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreatingPayment, setIsCreatingPayment] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const paymentStatusFromReturn = searchParams.get('status')

  async function loadPage() {
    if (!registrationId || !accessToken) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const registrations = await getMyRegistrations(accessToken)
      const ownRegistration = registrations.find(
        (item) => item.id === registrationId,
      )

      if (!ownRegistration) {
        setErrorMessage('Заявка не найдена или недоступна вашей учётной записи.')
        return
      }

      setRegistration(ownRegistration)

      try {
        const loadedPayment = await getPaymentForRegistration(
          registrationId,
          accessToken,
        )
        setPayment(loadedPayment)
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          setPayment(null)
        } else {
          throw error
        }
      }
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось загрузить данные заявки и оплаты.'),
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    if (!registrationId) {
      setErrorMessage('Идентификатор заявки не указан.')
      setIsLoading(false)
      return
    }

    loadPage()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, registrationId, navigate])

  async function handleCreatePayment() {
    if (!registrationId || !accessToken) {
      return
    }

    setIsCreatingPayment(true)
    setErrorMessage(null)

    try {
      const createdPayment = await createPaymentForRegistration(
        registrationId,
        accessToken,
      )

      setPayment(createdPayment)

      if (createdPayment.confirmationUrl) {
        window.location.assign(createdPayment.confirmationUrl)
      }
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось создать платёж.'),
      )
    } finally {
      setIsCreatingPayment(false)
    }
  }

  return (
    <section className="admin-section">
      <p className="eyebrow">Оплата участия</p>
      <h1>Заявка и статус оплаты</h1>

      {isLoading ? <p>Загрузка…</p> : null}

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

          <h2>Платёж</h2>

          {payment ? (
            <>
              <dl className="definition-list">
                <div>
                  <dt>Сумма</dt>
                  <dd>{payment.amount} {payment.currency}</dd>
                </div>
                <div>
                  <dt>Статус</dt>
                  <dd>{paymentStatusLabel(payment.status)}</dd>
                </div>
              </dl>

              {payment.confirmationUrl && payment.status === 'PENDING' ? (
                <a
                  className="button button-primary"
                  href={payment.confirmationUrl}
                >
                  Перейти к оплате
                </a>
              ) : null}
            </>
          ) : (
            <button
              className="button button-primary"
              type="button"
              onClick={handleCreatePayment}
              disabled={isCreatingPayment}
            >
              {isCreatingPayment ? 'Создаём платёж…' : 'Перейти к оплате'}
            </button>
          )}

          {paymentStatusFromReturn === 'success' ? (
            <div className="success-banner" role="status">
              Оплата успешно завершена. Статус платежа будет обновлён после
              подтверждения платёжного провайдера.
            </div>
          ) : null}

          {paymentStatusFromReturn === 'failed' ? (
            <div className="form-error" role="alert">
              Оплата не завершена. Попробуйте ещё раз или обратитесь к
              организатору.
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  )
}