import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getMyRegistrations } from '../../api/registrations.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import type {
  Registration,
  RegistrationStatus,
} from './registration.types'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

function getStatusLabel(status: RegistrationStatus) {
  const labels: Record<RegistrationStatus, string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    NEEDS_CORRECTION: 'Требует уточнения',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function getStatusClassName(status: RegistrationStatus) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: 'status-badge status-submitted',
    CONFIRMED: 'status-badge status-confirmed',
    REJECTED: 'status-badge status-rejected',
    NEEDS_CORRECTION: 'status-badge status-correction',
    CANCELLED: 'status-badge status-cancelled',
  }

  return classes[status]
}

function getParticipantName(registration: Registration) {
  const { firstName, lastName, middleName } = registration.participantSnapshot

  return [lastName, firstName, middleName].filter(Boolean).join(' ')
}

export function MyRegistrationsPage() {
  const { session } = useAuth()
  const [searchParams] = useSearchParams()
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function loadRegistrations() {
    if (!session) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const response = await getMyRegistrations(session.accessToken)
      setRegistrations(response)
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'Сессия истекла. Войдите снова.'
          : error instanceof ApiError
            ? error.message
            : 'Не удалось загрузить ваши заявки.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadRegistrations()
  }, [])

  const createdRegistrationId = searchParams.get('created')

  return (
    <section>
      <p className="eyebrow">Личный кабинет</p>

      <h1>Мои заявки</h1>

      <p className="page-description">
        Здесь отображается актуальный статус ваших заявок на соревнования.
      </p>

      {createdRegistrationId ? (
        <div className="success-banner" role="status">
          Заявка успешно отправлена и добавлена в список.
        </div>
      ) : null}

      {isLoading ? <LoadingState message="Загружаем ваши заявки…" /> : null}

      {!isLoading && errorMessage ? (
        <ErrorState
          message={errorMessage}
          onRetry={() => void loadRegistrations()}
        />
      ) : null}

      {!isLoading && !errorMessage && registrations.length === 0 ? (
        <div className="state-card">
          <h2>Заявок пока нет</h2>
          <p>Выберите соревнование и подайте первую заявку.</p>

          <Link className="button button-primary" to="/">
            Найти соревнование
          </Link>
        </div>
      ) : null}

            {!isLoading && !errorMessage && registrations.length > 0 && (
        <div className="registration-list">
          {registrations.map((registration) => (
            <article className="registration-card" key={registration.id}>
              <div className="registration-card-header">
                <div>
                  <p className="discipline-label">Заявка</p>
                  <h2>{getParticipantName(registration)}</h2>
                </div>

                <span className={getStatusClassName(registration.status)}>
                  {getStatusLabel(registration.status)}
                </span>
              </div>

              <dl className="registration-facts">
                <div>
                  <dt>Отправлена</dt>
                  <dd>{formatDate(registration.submittedAt)}</dd>
                </div>

                {registration.participantSnapshot.clubName ? (
                  <div>
                    <dt>Клуб</dt>
                    <dd>{registration.participantSnapshot.clubName}</dd>
                  </div>
                ) : null}

                {registration.participantSnapshot.city ? (
                  <div>
                    <dt>Город</dt>
                    <dd>{registration.participantSnapshot.city}</dd>
                  </div>
                ) : null}
              </dl>

              {registration.reviewNote ? (
                <div className="review-note">
                  <strong>Комментарий организатора</strong>
                  <p>{registration.reviewNote}</p>
                </div>
              ) : null}

              <p className="registration-technical-id">
                ID дисциплины: {registration.eventDisciplineId}
              </p>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}