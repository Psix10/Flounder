import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getRegistration,
  reviewRegistration,
  type Registration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import {
  confirmPayment,
  getPaymentForReview,
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
    SUCCEEDED: 'Оплачен',
    CANCELED: 'Отменён',
    FAILED: 'Ошибка оплаты',
  }

  return labels[status] ?? status
}

function registrationStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUBMITTED: 'Отправлена',
    CONFIRMED: 'Подтверждена',
    NEEDS_CORRECTION: 'Нужна корректировка',
    REJECTED: 'Отклонена',
    CANCELLED: 'Отменена',
  }

  return labels[status] ?? status
}

export function OperatorRegistrationPage() {
  const { registrationId } = useParams<{ registrationId: string }>()
  const { session, hasRole } = useAuth()
  const accessToken = session?.accessToken ?? null

  const [registration, setRegistration] = useState<Registration | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [reviewNote, setReviewNote] = useState('')

  const canReviewRegistration =
    hasRole('platform_admin') || hasRole('organizer')

  const canConfirmPayment =
    hasRole('platform_admin') || hasRole('organizer')

  async function loadData() {
    if (!registrationId || !accessToken) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const loadedRegistration = await getRegistration(
        registrationId,
        accessToken,
      )

      setRegistration(loadedRegistration)

      try {
        const loadedPayment = await getPaymentForReview(
          registrationId,
          accessToken,
        )
        setPayment(loadedPayment)
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          setPayment(null)
          return
        } else {
          throw error
        }
      }
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось загрузить данные заявки.'),
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [registrationId, accessToken])

  async function handleReview(decision: RegistrationReviewDecision) {
    if (!registration || !accessToken) {
      return
    }

    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await reviewRegistration(
        registration.id,
        {
          decision,
          reviewNote: reviewNote.trim(),
        },
        accessToken,
      )

      setRegistration(updated)
      setSuccessMessage('Статус заявки обновлён.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось обновить статус заявки.'),
      )
    } finally {
      setIsSaving(false)
    }
  }

  async function handleConfirmPayment() {
    if (!payment || !accessToken) {
      return
    }

    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await confirmPayment(payment.id, accessToken)
      setPayment(updated)
      setSuccessMessage('Платёж подтверждён.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось подтвердить платёж.'),
      )
    } finally {
      setIsSaving(false)
    }
  }

  if (!registrationId || !accessToken) {
    return null
  }

  if (isLoading) {
    return <section className="admin-section"><p>Загрузка заявки…</p></section>
  }

  if (errorMessage && !registration) {
    return (
      <section className="admin-section">
        <p className="form-error" role="alert">{errorMessage}</p>
        <NavLink to="/operator/payments">Вернуться к списку</NavLink>
      </section>
    )
  }

  if (!registration) {
    return null
  }

  return (
    <section className="admin-section">
        <NavLink to="/operator/payments">← К заявкам и платежам</NavLink>

        <p className="eyebrow">Панель оператора</p>
        <h1>Заявка</h1>

        <dl className="detail-list">
            <div>
            <dt>ID заявки</dt>
            <dd>{registration.id}</dd>
            </div>
            <div>
            <dt>Событие</dt>
            <dd>{registration.eventName ?? registration.eventId}</dd>
            </div>
            <div>
            <dt>Дисциплина</dt>
            <dd>{registration.eventDisciplineName ?? registration.eventDisciplineId}</dd>
            </div>
            <div>
            <dt>Статус</dt>
            <dd>{registrationStatusLabel(registration.status)}</dd>
            </div>
            {registration.reviewNote ? (
            <div>
                <dt>Комментарий проверки</dt>
                <dd>{registration.reviewNote}</dd>
            </div>
            ) : null}
        </dl>

        <h2>Платёж</h2>

        {payment ? (
            <dl className="detail-list">
            <div>
                <dt>ID платежа</dt>
                <dd>{payment.id}</dd>
            </div>
            <div>
                <dt>Сумма</dt>
                <dd>{payment.amount} {payment.currency}</dd>
            </div>
            <div>
                <dt>Провайдер</dt>
                <dd>{payment.provider}</dd>
            </div>
            <div>
                <dt>Статус</dt>
                <dd>{paymentStatusLabel(payment.status)}</dd>
            </div>
            </dl>
        ) : (
            <p>Платёж для этой заявки ещё не создан.</p>
        )}

        {canConfirmPayment && payment && payment.status === 'PENDING' ? (
            <button
            className="button button-primary"
            disabled={isSaving}
            onClick={handleConfirmPayment}
            >
            {isSaving ? 'Сохраняем…' : 'Подтвердить платёж'}
            </button>
        ) : null}

        {canReviewRegistration ? (
            <>
            <h2>Проверка заявки</h2>

            <label>
                <span>Комментарий</span>
                <textarea
                value={reviewNote}
                onChange={(event) => setReviewNote(event.target.value)}
                disabled={isSaving}
                rows={4}
                />
            </label>

            <div className="action-row">
                <button
                className="button button-primary"
                disabled={isSaving}
                onClick={() => handleReview('CONFIRMED')}
                >
                Подтвердить заявку
                </button>

                <button
                className="button button-secondary"
                disabled={isSaving}
                onClick={() => handleReview('NEEDS_CORRECTION')}
                >
                Вернуть на корректировку
                </button>

                <button
                className="button button-danger"
                disabled={isSaving}
                onClick={() => handleReview('REJECTED')}
                >
                Отклонить заявку
                </button>
            </div>
            </>
        ) : (
            <p className="page-description">
            У оператора есть доступ к просмотру. Подтверждение заявки и платежа
            доступно организатору или администратору.
            </p>
        )}

        {errorMessage ? (
            <p className="form-error" role="alert">{errorMessage}</p>
        ) : null}

        {successMessage ? (
            <p className="form-success" role="status">{successMessage}</p>
        ) : null}
        </section>
    )
}