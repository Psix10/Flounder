import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from '../../features/registrations/registration.types'
import {
  createPaymentForRegistration,
  getPaymentForRegistration,
  type Payment,
} from '../../api/payments.api'
import styles from './PaymentPage.module.css'

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите в систему снова.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для выполнения этого действия.'
    }

    if (error.status === 404) {
      return 'Заявка или платёж не найдены.'
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

function registrationStatusLabel(status: Registration['status']) {
  const labels: Record<Registration['status'], string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    NEEDS_CORRECTION: 'Требует уточнения',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function paymentStatusClassName(status: string) {
  const statusClasses: Record<string, string> = {
    PENDING: styles.statusPending,
    WAITING_FOR_CAPTURE: styles.statusWaiting,
    SUCCEEDED: styles.statusSucceeded,
    CANCELED: styles.statusCanceled,
    FAILED: styles.statusFailed,
  }

  return `${styles.statusBadge} ${
    statusClasses[status] ?? styles.statusNeutral
  }`
}

function getPaymentUnavailableCopy(registration: Registration) {
  if (registration.status === 'SUBMITTED') {
    return {
      title: 'Заявка находится на рассмотрении',
      message:
        'Организатор проверяет данные заявки. Возможность оплаты появится после подтверждения.',
    }
  }

  if (registration.status === 'NEEDS_CORRECTION') {
    return {
      title: 'Заявка требует уточнения',
      message:
        'Исправьте данные по комментарию организатора. Оплата станет доступна после повторного подтверждения заявки.',
    }
  }

  if (registration.status === 'REJECTED') {
    return {
      title: 'Заявка отклонена',
      message:
        'Оплата недоступна для отклонённой заявки.',
    }
  }

  if (registration.status === 'CANCELLED') {
    return {
      title: 'Заявка отменена',
      message:
        'Оплата недоступна для отменённой заявки.',
    }
  }

  return {
    title: 'Оплата пока недоступна',
    message:
      'Оплата станет доступна после подтверждения заявки организатором.',
  }
}

export function PaymentPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { registrationId } = useParams<{ registrationId: string }>()
  const [searchParams] = useSearchParams()
  const accessToken = session?.accessToken

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
        setRegistration(null)
        setPayment(null)
        setErrorMessage(
          'Заявка не найдена или недоступна вашей учётной записи.',
        )
        return
      }

      setRegistration(ownRegistration)

      if (ownRegistration.status !== 'CONFIRMED') {
        setPayment(null)
        return
      }

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
      navigate('/login', {
        replace: true,
        state: {
          from: registrationId
            ? `/my/registrations/${registrationId}/payment`
            : '/my/registrations',
        },
      })
      return
    }

    if (!registrationId) {
      setErrorMessage('Идентификатор заявки не указан.')
      setIsLoading(false)
      return
    }

    void loadPage()
    // loadPage uses the current registrationId and accessToken.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, registrationId, navigate])

  async function handleCreatePayment() {
    if (
      !registrationId ||
      !accessToken ||
      !registration ||
      registration.status !== 'CONFIRMED'
    ) {
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

  if (isLoading) {
    return (
      <section className={styles.page}>
        <div className={styles.stateCard}>
          <h2>Загрузка</h2>
          <p>Получаем данные заявки и информацию об оплате.</p>
        </div>
      </section>
    )
  }

  if (errorMessage && !registration) {
    return (
      <section className={styles.page}>
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>

        <Link className={styles.backLink} to="/my/registrations">
          ← К моим заявкам
        </Link>
      </section>
    )
  }

  if (!registration) {
    return null
  }

  const isConfirmed = registration.status === 'CONFIRMED'
  const unavailableCopy = getPaymentUnavailableCopy(registration)

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/my/registrations">
        ← К моим заявкам
      </Link>

      <p className={styles.eyebrow}>Оплата участия</p>

      <h1 className={styles.title}>Заявка и статус оплаты</h1>

      <p className={styles.description}>
        Проверьте данные заявки и перейдите к оплате участия после её
        подтверждения организатором.
      </p>

      <div className={styles.content}>
        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <div>
              <p className={styles.cardLabel}>Заявка</p>
              <h2 className={styles.cardTitle}>Участие в соревновании</h2>
            </div>

            <span className={styles.registrationBadge}>
              {registrationStatusLabel(registration.status)}
            </span>
          </div>

          <dl className={styles.facts}>
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
          </dl>

          {registration.reviewNote ? (
            <div className={styles.reviewNote}>
              <strong>Комментарий организатора</strong>
              <p>{registration.reviewNote}</p>
            </div>
          ) : null}
        </article>

        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <div>
              <p className={styles.cardLabel}>Платёж</p>
              <h2 className={styles.cardTitle}>Оплата участия</h2>
            </div>

            {payment ? (
              <span className={paymentStatusClassName(payment.status)}>
                {paymentStatusLabel(payment.status)}
              </span>
            ) : null}
          </div>

          {!isConfirmed ? (
            <div className={styles.paymentUnavailable}>
              <strong>{unavailableCopy.title}</strong>

              <p>{unavailableCopy.message}</p>
            </div>
          ) : payment ? (
            <>
              <dl className={styles.facts}>
                <div>
                  <dt>Сумма</dt>
                  <dd className={styles.paymentAmount}>
                    {payment.amount} {payment.currency}
                  </dd>
                </div>

                <div>
                  <dt>Статус</dt>
                  <dd>{paymentStatusLabel(payment.status)}</dd>
                </div>

                <div>
                  <dt>Способ оплаты</dt>
                  <dd>{payment.provider}</dd>
                </div>
              </dl>

              {payment.confirmationUrl && payment.status === 'PENDING' ? (
                <a
                  className={styles.primaryButton}
                  href={payment.confirmationUrl}
                >
                  Перейти к оплате
                </a>
              ) : null}

              {payment.status === 'SUCCEEDED' ? (
                <div className={styles.paymentSuccess}>
                  Оплата подтверждена. Ваша заявка готова к участию в
                  соревновании.
                </div>
              ) : null}

              {payment.status === 'CANCELED' ||
              payment.status === 'FAILED' ? (
                <div className={styles.paymentFailure}>
                  Оплата не завершена. При необходимости создайте новый
                  платёж или обратитесь к организатору.
                </div>
              ) : null}
            </>
          ) : (
            <>
              <p className={styles.paymentDescription}>
                Заявка подтверждена. Создайте платёж, чтобы перейти к оплате
                участия.
              </p>

              <button
                className={styles.primaryButton}
                disabled={isCreatingPayment}
                onClick={handleCreatePayment}
                type="button"
              >
                {isCreatingPayment
                  ? 'Создаём платёж…'
                  : 'Создать платёж'}
              </button>
            </>
          )}
        </article>

        {paymentStatusFromReturn === 'success' ? (
          <div className={styles.successBanner} role="status">
            Оплата успешно завершена. Статус платежа будет обновлён после
            подтверждения платёжного провайдера.
          </div>
        ) : null}

        {paymentStatusFromReturn === 'failed' ? (
          <div className={styles.errorBanner} role="alert">
            Оплата не завершена. Попробуйте ещё раз или обратитесь к
            организатору.
          </div>
        ) : null}

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}
      </div>
    </section>
  )
}