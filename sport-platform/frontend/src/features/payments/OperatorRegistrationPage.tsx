import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  getRegistration,
  reviewRegistration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from '../../features/registrations/registration.types'
import {
  confirmPayment,
  getPaymentForReview,
  type Payment,
} from '../../api/payments.api'
import styles from './OperatorRegistrationPage.module.css'

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

function registrationStatusLabel(status: Registration['status']) {
  const labels: Record<Registration['status'], string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    NEEDS_CORRECTION: 'Требует уточнения',
    REJECTED: 'Отклонена',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function registrationStatusClassName(status: Registration['status']) {
  const classes: Record<Registration['status'], string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    NEEDS_CORRECTION: styles.statusCorrection,
    REJECTED: styles.statusRejected,
    CANCELLED: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status]}`
}

function paymentStatusClassName(status: string) {
  const classes: Record<string, string> = {
    PENDING: styles.paymentPending,
    WAITING_FOR_CAPTURE: styles.paymentWaiting,
    SUCCEEDED: styles.paymentSucceeded,
    CANCELED: styles.paymentCancelled,
    FAILED: styles.paymentFailed,
  }

  return `${styles.statusBadge} ${classes[status] ?? styles.statusNeutral}`
}

export function OperatorRegistrationPage() {
  const { registrationId } = useParams<{ registrationId: string }>()
  const { session, hasRole } = useAuth()
  const accessToken = session?.accessToken

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

  useEffect(() => {
    if (!registrationId || !accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadData() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const loadedRegistration = await getRegistration(
          registrationId,
          authenticatedAccessToken,
        )

        if (!isMounted) {
          return
        }

        setRegistration(loadedRegistration)
        setReviewNote(loadedRegistration.reviewNote ?? '')

        try {
          const loadedPayment = await getPaymentForReview(
            registrationId,
            authenticatedAccessToken,
          )

          if (isMounted) {
            setPayment(loadedPayment)
          }
        } catch (error) {
          if (error instanceof ApiError && error.status === 404) {
            if (isMounted) {
              setPayment(null)
            }
          } else {
            throw error
          }
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(error, 'Не удалось загрузить данные заявки.'),
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
      setReviewNote(updated.reviewNote ?? '')
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
    return (
      <section className={styles.page}>
        <div className={styles.stateCard}>Загрузка заявки…</div>
      </section>
    )
  }

  if (errorMessage && !registration) {
    return (
      <section className={styles.page}>
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>

        <NavLink className={styles.backLink} to="/operator/payments">
          ← Вернуться к списку
        </NavLink>
      </section>
    )
  }

  if (!registration) {
    return null
  }

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/operator/payments">
        ← К заявкам и платежам
      </NavLink>

      <p className={styles.eyebrow}>Панель оператора</p>

      <div className={styles.titleRow}>
        <h1 className={styles.title}>Заявка</h1>

        <span className={registrationStatusClassName(registration.status)}>
          {registrationStatusLabel(registration.status)}
        </span>
      </div>

      <div className={styles.content}>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Данные заявки</h2>

          <dl className={styles.detailList}>
            <div>
              <dt>ID заявки</dt>
              <dd className={styles.technicalValue}>{registration.id}</dd>
            </div>

            <div>
              <dt>ID события</dt>
              <dd className={styles.technicalValue}>
                {registration.eventId}
              </dd>
            </div>

            <div>
              <dt>ID дисциплины</dt>
              <dd className={styles.technicalValue}>
                {registration.eventDisciplineId}
              </dd>
            </div>

            <div>
              <dt>Статус</dt>
              <dd>{registrationStatusLabel(registration.status)}</dd>
            </div>

            {registration.reviewNote ? (
              <div className={styles.reviewNote}>
                <dt>Комментарий проверки</dt>
                <dd>{registration.reviewNote}</dd>
              </div>
            ) : null}
          </dl>
        </article>

        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <h2 className={styles.cardTitle}>Платёж</h2>

            {payment ? (
              <span className={paymentStatusClassName(payment.status)}>
                {paymentStatusLabel(payment.status)}
              </span>
            ) : null}
          </div>

          {payment ? (
            <>
              <dl className={styles.detailList}>
                <div>
                  <dt>ID платежа</dt>
                  <dd className={styles.technicalValue}>{payment.id}</dd>
                </div>

                <div>
                  <dt>Сумма</dt>
                  <dd className={styles.amount}>
                    {payment.amount} {payment.currency}
                  </dd>
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

              {canConfirmPayment && payment.status === 'PENDING' ? (
                <button
                  className={styles.primaryButton}
                  disabled={isSaving}
                  onClick={handleConfirmPayment}
                  type="button"
                >
                  {isSaving ? 'Сохраняем…' : 'Подтвердить платёж'}
                </button>
              ) : null}
            </>
          ) : (
            <p className={styles.emptyText}>
              Платёж для этой заявки ещё не создан.
            </p>
          )}
        </article>

        {canReviewRegistration ? (
          <article className={styles.card}>
            <h2 className={styles.cardTitle}>Проверка заявки</h2>

            <label className={styles.noteField}>
              <span>Комментарий организатора</span>
              <textarea
                disabled={isSaving}
                onChange={(event) => setReviewNote(event.target.value)}
                rows={4}
                value={reviewNote}
              />
            </label>

            <div className={styles.actionRow}>
              <button
                className={styles.primaryButton}
                disabled={isSaving}
                onClick={() => handleReview('CONFIRMED')}
                type="button"
              >
                Подтвердить заявку
              </button>

              <button
                className={styles.secondaryButton}
                disabled={isSaving}
                onClick={() => handleReview('NEEDS_CORRECTION')}
                type="button"
              >
                Вернуть на корректировку
              </button>

              <button
                className={styles.dangerButton}
                disabled={isSaving}
                onClick={() => handleReview('REJECTED')}
                type="button"
              >
                Отклонить заявку
              </button>
            </div>
          </article>
        ) : (
          <div className={styles.readOnlyNotice}>
            У оператора есть доступ к просмотру. Подтверждение заявки и
            платежа доступно организатору или администратору.
          </div>
        )}

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}

        {successMessage ? (
          <p className={styles.successMessage} role="status">
            {successMessage}
          </p>
        ) : null}
      </div>
    </section>
  )
}