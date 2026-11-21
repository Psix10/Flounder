import { registrationEventTitle, registrationDisciplineName, mergeRegistrationDisplay } from '../../lib/registration-display.mjs'
import { registrationStatusLabel as getStatusLabel } from './registration-withdrawal.mjs'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'

import { ApiError } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { RegistrationCorrectionForm } from './RegistrationCorrectionForm'
import { RegistrationCorrectionHistory } from './RegistrationCorrectionHistory'
import { RegistrationWithdrawalHistory } from './RegistrationWithdrawalHistory'
import type {
  Registration,
  RegistrationStatus,
} from './registration.types'
import styles from './MyRegistrationsPage.module.css'

function formatDate(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return '—'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(date)
}

function formatSeedTime(value: unknown): string {
  if (value === undefined || value === null) {
    return 'Без заявочного времени (NT)'
  }

  if (
    typeof value !== 'number' ||
    !Number.isFinite(value) ||
    value <= 0
  ) {
    return 'Некорректное заявочное время'
  }

  const scaled = value * 100
  const totalHundredths = Math.round(scaled)

  if (
    !Number.isSafeInteger(totalHundredths) ||
    totalHundredths <= 0 ||
    Math.abs(scaled - totalHundredths) > 0.000001
  ) {
    return 'Некорректное заявочное время'
  }

  const minutes = Math.floor(totalHundredths / 6000)
  const seconds = Math.floor((totalHundredths % 6000) / 100)
  const hundredths = totalHundredths % 100

  return (
    `${String(minutes).padStart(2, '0')}:` +
    `${String(seconds).padStart(2, '0')}.` +
    String(hundredths).padStart(2, '0')
  )
}

function getSeedTimeLabel(registration: Registration): string {
  const meta: unknown = registration.registrationMeta

  if (meta === undefined || meta === null) {
    return formatSeedTime(undefined)
  }

  if (typeof meta !== 'object' || Array.isArray(meta)) {
    return 'Некорректные данные заявки'
  }

  if (!('seedTimeSeconds' in meta)) {
    return formatSeedTime(undefined)
  }

  return formatSeedTime(meta.seedTimeSeconds)
}


function getStatusClassName(status: RegistrationStatus): string {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    REJECTED: styles.statusRejected,
    NEEDS_CORRECTION: styles.statusCorrection,
    CANCELLED: styles.statusCancelled,
    WITHDRAWN: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status] ?? ''}`
}

function getParticipantName(registration: Registration): string {
  const { firstName, lastName, middleName } =
    registration.participantSnapshot

  return [lastName, firstName, middleName].filter(Boolean).join(' ')
}

function getStatusHint(status: RegistrationStatus): string | null {
  const hints: Record<RegistrationStatus, string | null> = {
    SUBMITTED:
      'Организатор проверяет заявку. Если вы отправили исправления, дождитесь повторного рассмотрения.',
    CONFIRMED:
      'Заявка подтверждена. Перейдите к оплате участия.',
    NEEDS_CORRECTION:
      'Прочитайте замечание организатора, внесите исправления и отправьте заявку на повторную проверку через форму ниже.',
    REJECTED:
      'Заявка отклонена. Оплата для неё недоступна.',
    CANCELLED:
      'Заявка отменена. Оплата для неё недоступна.',
    WITHDRAWN:
      'Заявка снята с участия. Создание оплаты и отправка исправлений недоступны. Причина указана в комментарии и истории.',
  }

  return hints[status]
}

function getStatusHintClassName(status: RegistrationStatus): string {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.hintSubmitted,
    CONFIRMED: styles.hintConfirmed,
    NEEDS_CORRECTION: styles.hintCorrection,
    REJECTED: styles.hintRejected,
    CANCELLED: styles.hintCancelled,
    WITHDRAWN: styles.hintCancelled,
  }

  return `${styles.statusHint} ${classes[status] ?? ''}`
}

export function MyRegistrationsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [searchParams] = useSearchParams()

  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true

    async function load() {
      if (!accessToken) {
        setRegistrations([])
        setIsLoading(false)
        return
      }

      setIsLoading(true)
      setErrorMessage(null)

      try {
        const response = await getMyRegistrations(accessToken)

        if (active) {
          setRegistrations(response)
        }
      } catch (error) {
        if (active) {
          setErrorMessage(
            error instanceof ApiError && error.status === 401
              ? 'Сессия истекла. Войдите снова.'
              : error instanceof ApiError
                ? error.message
                : 'Не удалось загрузить ваши заявки.',
          )
        }
      } finally {
        if (active) {
          setIsLoading(false)
        }
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [accessToken, reloadKey])

  function handleUpdated(updated: Registration) {
    setRegistrations((current) =>
      current.map((registration) =>
        registration.id === updated.id ? mergeRegistrationDisplay(registration, updated) : registration,
      ),
    )

    setSuccessMessage(
      'Исправления отправлены. Заявка снова находится на рассмотрении.',
    )
  }

  const createdRegistrationId = searchParams.get('created')

  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>Личный кабинет</p>
      <h1 className={styles.title}>Мои заявки</h1>

      <p className={styles.description}>
        Здесь отображается актуальный статус ваших заявок на соревнования.
      </p>

      {successMessage || createdRegistrationId ? (
        <div className={styles.successBanner} role="status">
          {successMessage ??
            'Заявка успешно отправлена и добавлена в список.'}
        </div>
      ) : null}

      {isLoading ? (
        <LoadingState message="Загружаем ваши заявки…" />
      ) : null}

      {!isLoading && errorMessage ? (
        <ErrorState
          message={errorMessage}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : null}

      {!isLoading && !errorMessage && registrations.length === 0 ? (
        <div className={styles.emptyState}>
          <h2>Заявок пока нет</h2>
          <p>Выберите соревнование и подайте первую заявку.</p>

          <Link className={styles.primaryButton} to="/">
            Найти соревнование
          </Link>
        </div>
      ) : null}

      {!isLoading && !errorMessage && registrations.length > 0 ? (
        <div className={styles.list}>
          {registrations.map((registration) => {
            const statusHint = getStatusHint(registration.status)

            return (
              <article className={styles.card} key={registration.id}>
                <div className={styles.cardHeader}>
                  <div>
                    <p className={styles.cardLabel}>Заявка</p>

                    <h2 className={styles.participantName}>
                      {getParticipantName(registration)}
                    </h2>
                  </div>

                  <span
                    className={getStatusClassName(registration.status)}
                  >
                    {getStatusLabel(registration.status)}
                  </span>
                </div>

                <dl className={styles.facts}>
                  <div>
                    <dt>Мероприятие</dt>
                    <dd>{registrationEventTitle(registration)}</dd>
                  </div>
                  <div>
                    <dt>Дисциплина</dt>
                    <dd>{registrationDisciplineName(registration)}</dd>
                  </div>
                  <div>
                    <dt>Заявочное время</dt>
                    <dd>{getSeedTimeLabel(registration)}</dd>
                  </div>

                  <div>
                    <dt>Последняя отправка</dt>
                    <dd>{formatDate(registration.submittedAt)}</dd>
                  </div>

                  {registration.participantSnapshot.clubName ? (
                    <div>
                      <dt>Клуб</dt>
                      <dd>
                        {registration.participantSnapshot.clubName}
                      </dd>
                    </div>
                  ) : null}

                  {registration.participantSnapshot.city ? (
                    <div>
                      <dt>Город</dt>
                      <dd>{registration.participantSnapshot.city}</dd>
                    </div>
                  ) : null}
                </dl>

                {statusHint ? (
                  <p
                    className={getStatusHintClassName(
                      registration.status,
                    )}
                  >
                    {statusHint}
                  </p>
                ) : null}

                {registration.reviewNote ? (
                  <div className={styles.reviewNote}>
                    <strong>Комментарий организатора</strong>
                    <p>{registration.reviewNote}</p>
                  </div>
                ) : null}

                <RegistrationCorrectionHistory
                  items={registration.registrationMeta?.correctionHistory}
                />

                <RegistrationWithdrawalHistory
                  items={registration.registrationMeta?.withdrawalHistory}
                />

                {registration.status === 'NEEDS_CORRECTION' &&
                accessToken ? (
                  <RegistrationCorrectionForm
                    key={`${registration.id}:${registration.updatedAt}`}
                    registration={registration}
                    accessToken={accessToken}
                    onUpdated={handleUpdated}
                  />
                ) : null}

                {registration.status === 'CONFIRMED' ? (
                  <div className={styles.actions}>
                    <Link
                      className={styles.primaryButton}
                      to={`/my/registrations/${encodeURIComponent(
                        registration.id,
                      )}/payment`}
                    >
                      Перейти к оплате
                    </Link>
                  </div>
                ) : null}


              </article>
            )
          })}
        </div>
      ) : null}
    </section>
  )
}
