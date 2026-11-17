import { registrationStatusLabel } from '../registrations/registration-withdrawal.mjs'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useParams } from 'react-router'

import { ApiError } from '../../api/http'
import {
  getRegistration,
  reviewRegistration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import {
  confirmPayment,
  getPaymentForReview,
  type Payment,
} from '../../api/payments.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { RegistrationCorrectionHistory } from '../registrations/RegistrationCorrectionHistory'
import { RegistrationWithdrawalHistory } from '../registrations/RegistrationWithdrawalHistory'
import { RegistrationWithdrawalForm } from '../registrations/RegistrationWithdrawalForm'
import { formatRegistrationSeedTime } from '../registrations/registrationTime'
import type {
  Registration,
  RegistrationStatus,
} from '../registrations/registration.types'
import styles from './OperatorRegistrationPage.module.css'

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для этого действия.'
    }

    if (error.status === 404) {
      return 'Заявка или платёж не найдены.'
    }

    if (error.code === 'registrations.notreviewable') {
      return 'Статус заявки изменился. Обновите страницу перед рассмотрением.'
    }

    return error.message || fallback
  }

  return fallback
}

function paymentStatusLabel(status: string) {
  const labels: Record<string, string> = {
    CREATED: 'Создан',
    PENDING: 'Ожидает оплаты',
    WAITING_FOR_CAPTURE: 'Ожидает подтверждения',
    SUCCEEDED: 'Оплачен',
    CANCELED: 'Отменён',
    FAILED: 'Ошибка оплаты',
    REFUNDED: 'Возвращён',
  }

  return labels[status] ?? status
}


function registrationStatusClassName(status: RegistrationStatus) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    NEEDS_CORRECTION: styles.statusCorrection,
    REJECTED: styles.statusRejected,
    CANCELLED: styles.statusCancelled,
    WITHDRAWN: styles.statusNeutral,
  }

  return `${styles.statusBadge} ${
    classes[status] ?? styles.statusNeutral
  }`
}

function paymentStatusClassName(status: string) {
  const classes: Record<string, string> = {
    CREATED: styles.paymentPending,
    PENDING: styles.paymentPending,
    WAITING_FOR_CAPTURE: styles.paymentWaiting,
    SUCCEEDED: styles.paymentSucceeded,
    CANCELED: styles.paymentCancelled,
    FAILED: styles.paymentFailed,
    REFUNDED: styles.statusNeutral,
  }

  return `${styles.statusBadge} ${
    classes[status] ?? styles.statusNeutral
  }`
}

function getSeedTimeLabel(registration: Registration): string {
  const meta: unknown = registration.registrationMeta

  if (meta === undefined || meta === null) {
    return formatRegistrationSeedTime(undefined)
  }

  if (typeof meta !== 'object' || Array.isArray(meta)) {
    return 'Некорректные данные заявки'
  }

  if (!('seedTimeSeconds' in meta)) {
    return formatRegistrationSeedTime(undefined)
  }

  return formatRegistrationSeedTime(meta.seedTimeSeconds)
}

export function OperatorRegistrationPage() {
  const { registrationId } = useParams<{ registrationId: string }>()
  const { session, hasRole } = useAuth()
  const accessToken = session?.accessToken

  const [registration, setRegistration] =
    useState<Registration | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [reviewNote, setReviewNote] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const saving = useRef(false)
  const pageEpoch = useRef(0)
  const [paymentRefreshing, setPaymentRefreshing] = useState(false)
  const [paymentLoadError, setPaymentLoadError] = useState<string | null>(null)

  const canReviewRegistration =
    hasRole('platform_admin') || hasRole('organizer')

  const canReviewCurrentRegistration =
    canReviewRegistration && registration?.status === 'SUBMITTED'

  const canConfirmPayment =
    hasRole('platform_admin') || hasRole('operator')

  const canManuallyConfirmPayment =
    canConfirmPayment &&
    payment?.provider === 'MANUAL' &&
    (payment.status === 'CREATED' || payment.status === 'PENDING')

  useEffect(() => {
    let active = true
    const epoch = ++pageEpoch.current

    async function loadData() {
      setRegistration(null)
      setPayment(null)
      setPaymentRefreshing(false)
      setPaymentLoadError(null)
      setReviewNote('')
      setErrorMessage(null)
      setSuccessMessage(null)

      if (!registrationId || !accessToken) {
        setIsLoading(false)
        return
      }

      setIsLoading(true)

      try {
        const loadedRegistration = await getRegistration(
          registrationId,
          accessToken,
        )

        if (!active) {
          return
        }

        setRegistration(loadedRegistration)

        try {
          const loadedPayment = await getPaymentForReview(
            registrationId,
            accessToken,
          )

          if (active) {
            setPayment(loadedPayment)
          }
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 404)) {
            if (active) setPaymentLoadError('Не удалось получить актуальный статус платежа. Обновите данные.')
            throw error
          }
        }
      } catch (error) {
        if (active) {
          setErrorMessage(
            getErrorMessage(
              error,
              'Не удалось загрузить заявку и данные платежа.',
            ),
          )
        }
      } finally {
        if (active) {
          setIsLoading(false)
        }
      }
    }

    void loadData()

    return () => {
      active = false
      if (pageEpoch.current === epoch) pageEpoch.current++
    }
  }, [accessToken, registrationId, reloadKey])

  async function handleReview(decision: RegistrationReviewDecision) {
    if (
      !registration ||
      !accessToken ||
      !canReviewCurrentRegistration ||
      saving.current
    ) {
      return
    }

    const note = reviewNote.trim()

    if (
      (decision === 'NEEDS_CORRECTION' || decision === 'REJECTED') &&
      !note
    ) {
      setErrorMessage(
        'Укажите причину отклонения или поясните, что нужно уточнить.',
      )
      setSuccessMessage(null)
      return
    }

    saving.current = true
    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await reviewRegistration(
        registration.id,
        { decision, reviewNote: note },
        accessToken,
      )

      setRegistration(updated)
      setReviewNote('')
      setSuccessMessage('Решение по заявке сохранено.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось сохранить решение по заявке.'),
      )
    } finally {
      saving.current = false
      setIsSaving(false)
    }
  }

  async function handleConfirmPayment() {
    if (
      !payment ||
      !accessToken ||
      !canManuallyConfirmPayment ||
      saving.current
    ) {
      return
    }

    saving.current = true
    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await confirmPayment(payment.id, accessToken)

      setPayment(updated)
      setSuccessMessage('Ручная оплата подтверждена.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось подтвердить платёж.'),
      )
    } finally {
      saving.current = false
      setIsSaving(false)
    }
  }

  async function handleWithdrawn(updated: Registration) {
    if (!accessToken) return
    const epoch = pageEpoch.current
    setRegistration(updated)
    setPayment(null)
    setPaymentRefreshing(true)
    setPaymentLoadError(null)
    setErrorMessage(null)
    setSuccessMessage('Заявка снята с участия. Обновляем сведения о платеже…')
    try {
      const actual = await getPaymentForReview(updated.id, accessToken)
      if (pageEpoch.current !== epoch) return
      setPayment(actual)
      setSuccessMessage('Заявка снята с участия. Актуальные сведения о платеже загружены.')
    } catch (error) {
      if (pageEpoch.current !== epoch) return
      if (error instanceof ApiError && error.status === 404) {
        setPayment(null)
        setSuccessMessage('Заявка снята с участия. Связанный платёж не найден.')
      } else {
        setPaymentLoadError('Заявка уже снята, но актуальный статус платежа не загружен. Нажмите «Обновить». Повторять снятие не нужно.')
        setSuccessMessage('Заявка снята с участия. Требуется обновить сведения о платеже.')
      }
    } finally {
      if (pageEpoch.current === epoch) setPaymentRefreshing(false)
    }
  }

  if (!registrationId || !accessToken) {
    return null
  }

  if (isLoading) {
    return (
      <section className={styles.page}>
        <div className={styles.stateCard} role="status">
          Загружаем заявку и данные платежа…
        </div>
      </section>
    )
  }

  const backTo =
    canReviewRegistration && registration
      ? `/organizer/events/${encodeURIComponent(
          registration.eventId,
        )}/registrations`
      : canReviewRegistration
        ? '/organizer/events'
        : '/operator/payments'

  if (!registration) {
    return (
      <section className={styles.page}>
        <NavLink className={styles.backLink} to={backTo}>
          ← Назад
        </NavLink>

        <p className={styles.errorMessage} role="alert">
          {errorMessage || 'Не удалось загрузить заявку.'}
        </p>

        <button
          className={styles.secondaryButton}
          type="button"
          onClick={() => setReloadKey((value) => value + 1)}
        >
          Повторить загрузку
        </button>
      </section>
    )
  }

  const meta = registration.registrationMeta

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to={backTo}>
        ← К списку заявок
      </NavLink>

      <p className={styles.eyebrow}>Проверка заявки</p>

      <div className={styles.titleRow}>
        <h1 className={styles.title}>Заявка участника</h1>

        <span
          className={registrationStatusClassName(registration.status)}
        >
          {registrationStatusLabel(registration.status)}
        </span>
      </div>

      <div className={styles.content}>
        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <h2 className={styles.cardTitle}>Данные заявки</h2>

            <button
              className={styles.secondaryButton}
              type="button"
              disabled={isSaving}
              onClick={() => setReloadKey((value) => value + 1)}
            >
              Обновить
            </button>
          </div>

          <dl className={styles.detailList}>
            <div>
              <dt>Участник</dt>
              <dd>
                {[
                  registration.participantSnapshot.lastName,
                  registration.participantSnapshot.firstName,
                  registration.participantSnapshot.middleName,
                ]
                  .filter(Boolean)
                  .join(' ')}
              </dd>
            </div>

            <div>
              <dt>Заявочное время</dt>
              <dd>{getSeedTimeLabel(registration)}</dd>
            </div>

            <div>
              <dt>ID заявки</dt>
              <dd className={styles.technicalValue}>
                {registration.id}
              </dd>
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
              <dt>Контактное лицо для экстренной связи</dt>
              <dd>{meta?.emergencyContactName || '—'}</dd>
            </div>

            <div>
              <dt>Телефон для экстренной связи</dt>
              <dd>{meta?.emergencyContactPhone || '—'}</dd>
            </div>

            <div>
              <dt>Наличие справки со слов участника</dt>
              <dd>
                {meta?.medicalCertificate
                  ? 'Подтверждено'
                  : 'Не подтверждено'}
              </dd>
            </div>

            <div>
              <dt>Статус</dt>
              <dd>{registrationStatusLabel(registration.status)}</dd>
            </div>

            {registration.reviewNote ? (
              <div className={styles.reviewNote}>
                <dt>Последний комментарий организатора</dt>
                <dd>{registration.reviewNote}</dd>
              </div>
            ) : null}
          </dl>

          <RegistrationCorrectionHistory
            items={meta?.correctionHistory}
          />
          <RegistrationWithdrawalHistory items={meta?.withdrawalHistory} />
        </article>

        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <h2 className={styles.cardTitle}>Оплата</h2>

            {payment ? (
              <span className={paymentStatusClassName(payment.status)}>
                {paymentStatusLabel(payment.status)}
              </span>
            ) : null}
          </div>

          {paymentRefreshing ? (
            <p className={styles.emptyText} role="status">Обновляем статус платежа…</p>
          ) : paymentLoadError ? (
            <p className={styles.errorMessage} role="alert">{paymentLoadError}</p>
          ) : payment ? (
            <>
              <dl className={styles.detailList}>
                <div>
                  <dt>ID платежа</dt>
                  <dd className={styles.technicalValue}>
                    {payment.id}
                  </dd>
                </div>

                <div>
                  <dt>Сумма</dt>
                  <dd className={styles.amount}>
                    {payment.amount} {payment.currency}
                  </dd>
                </div>

                <div>
                  <dt>Способ оплаты</dt>
                  <dd>{payment.provider}</dd>
                </div>

                <div>
                  <dt>Статус платежа</dt>
                  <dd>{paymentStatusLabel(payment.status)}</dd>
                </div>
              </dl>

              {canManuallyConfirmPayment ? (
                <button
                  className={styles.primaryButton}
                  disabled={isSaving}
                  onClick={() => void handleConfirmPayment()}
                  type="button"
                >
                  {isSaving
                    ? 'Подтверждаем оплату…'
                    : 'Подтвердить ручную оплату'}
                </button>
              ) : null}

              {payment.provider === 'YOOKASSA' ? (
                <p className={styles.emptyText}>
                  Оплата через YooKassa подтверждается автоматически
                  через webhook платёжного провайдера.
                </p>
              ) : null}
            </>
          ) : (
            <p className={styles.emptyText}>
              Платёж для этой заявки ещё не создан.
            </p>
          )}
        </article>

        {canReviewRegistration && registration.status === 'CONFIRMED' ? (
          <article className={styles.card}>
            <h2 className={styles.cardTitle}>Снятие с участия</h2>
            <RegistrationWithdrawalForm
              key={`${registration.id}:${registration.updatedAt}`}
              registration={registration}
              accessToken={accessToken}
              disabled={isSaving || paymentRefreshing}
              beforeSubmit={() => !saving.current}
              onBusyChange={(busy) => { saving.current = busy; setIsSaving(busy) }}
              onUpdated={handleWithdrawn}
              onRefresh={() => setReloadKey((value) => value + 1)}
            />
          </article>
        ) : null}

        {canReviewCurrentRegistration ? (
          <article className={styles.card}>
            <h2 className={styles.cardTitle}>Решение по заявке</h2>

            <label className={styles.noteField}>
              <span>Новый комментарий для участника</span>

              <textarea
                disabled={isSaving}
                maxLength={2000}
                onChange={(event) => setReviewNote(event.target.value)}
                rows={4}
                value={reviewNote}
              />
            </label>

            <div className={styles.actionRow}>
              <button
                className={styles.primaryButton}
                disabled={isSaving}
                onClick={() => void handleReview('CONFIRMED')}
                type="button"
              >
                Подтвердить заявку
              </button>

              <button
                className={styles.secondaryButton}
                disabled={isSaving}
                onClick={() => void handleReview('NEEDS_CORRECTION')}
                type="button"
              >
                Запросить уточнение
              </button>

              <button
                className={styles.dangerButton}
                disabled={isSaving}
                onClick={() => void handleReview('REJECTED')}
                type="button"
              >
                Отклонить заявку
              </button>
            </div>
          </article>
        ) : (
          <div className={styles.readOnlyNotice} role="status">
            {registration.status === 'WITHDRAWN'
              ? 'Заявка снята с участия. Причина и история сохранены. Повторное рассмотрение и повторное снятие недоступны.'
              : registration.status === 'NEEDS_CORRECTION'
              ? 'Ожидаем исправления и ответ участника. После повторной отправки нажмите «Обновить»: заявка снова станет доступна для рассмотрения.'
              : canReviewRegistration
                ? 'Для текущего статуса повторное рассмотрение заявки недоступно.'
                : 'Оператор может просматривать заявку и подтверждать ручную оплату. Решение по заявке принимает организатор или администратор.'}
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
