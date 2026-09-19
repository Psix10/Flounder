import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getPublicEventDetails } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import type {
  DisciplineSettings,
  PublicEventDetails,
} from './event.types'

function formatDate(value: string | null) {
  if (!value) {
    return 'Не указано'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

function parseSettings(settingsJson: string | null): DisciplineSettings {
  if (!settingsJson) {
    return {}
  }

  try {
    const parsed: unknown = JSON.parse(settingsJson)

    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return {}
    }

    return parsed as DisciplineSettings
  } catch {
    return {}
  }
}

function getGenderLabel(value: string | undefined) {
  const labels: Record<string, string> = {
    OPEN: 'Открытая категория',
    MALE: 'Мужчины',
    FEMALE: 'Женщины',
  }

  return value ? (labels[value] ?? value) : null
}

function getAgeGroupLabel(value: string | undefined) {
  const labels: Record<string, string> = {
    ADULT: 'Взрослые',
    JUNIOR: 'Юниоры',
    CHILDREN: 'Дети',
  }

  return value ? (labels[value] ?? value) : null
}

export function EventDetailsPage() {
  const { eventCode } = useParams()
  const [event, setEvent] = useState<PublicEventDetails | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function loadEvent() {
    if (!eventCode) {
      setErrorMessage('Не указан идентификатор события.')
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const response = await getPublicEventDetails(eventCode)
      setEvent(response)
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.status === 404
            ? 'Событие не найдено или больше не опубликовано.'
            : error.message
          : 'Не удалось загрузить данные события.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadEvent()
  }, [eventCode])

  if (isLoading) {
    return <LoadingState message="Загружаем информацию о соревновании…" />
  }

  if (errorMessage) {
    return (
      <ErrorState
        message={errorMessage}
        onRetry={() => void loadEvent()}
      />
    )
  }

  if (!event) {
    return <ErrorState message="Событие не найдено." />
  }

  const disciplines = event.disciplines ?? []

  return (
    <section>
      <Link className="back-link" to="/">
        ← Все события
      </Link>

      <p className="eyebrow">Соревнование</p>

      <h1>{event.title}</h1>

      {event.description ? (
        <p className="page-description">{event.description}</p>
      ) : null}

      <div className="event-details-meta">
        <div>
          <span>Регистрация</span>
          <strong>
            {formatDate(event.registrationOpenAt)} —{' '}
            {formatDate(event.registrationCloseAt)}
          </strong>
        </div>

        <div>
          <span>Дата события</span>
          <strong>
            {formatDate(event.eventStartAt)} — {formatDate(event.eventEndAt)}
          </strong>
        </div>
      </div>

      <div className="section-heading">
        <div>
          <p className="eyebrow">Выбор дисциплины</p>
          <h2>Доступные дисциплины</h2>
        </div>
      </div>

      {disciplines.length === 0 ? (
        <div className="state-card">
          Для этого события пока нет доступных дисциплин.
        </div>
      ) : (
        <div className="discipline-list">
          {disciplines.map((discipline) => {
            const settings = parseSettings(discipline.settingsJson)
            const gender = getGenderLabel(settings.gender)
            const ageGroup = getAgeGroupLabel(settings.ageGroup)

            return (
              <article className="discipline-card" key={discipline.id}>
                <div className="discipline-card-top">
                  <div>
                    <p className="discipline-label">Дисциплина</p>
                    <h3>{discipline.name}</h3>
                  </div>

                  <strong className="discipline-price">
                    {formatMoney(
                      discipline.entryFeeAmount,
                      discipline.entryFeeCurrency,
                    )}
                  </strong>
                </div>

                <div className="discipline-facts">
                  {settings.distanceMeters ? (
                    <span>{settings.distanceMeters} м</span>
                  ) : null}

                  {settings.poolLengthMeters ? (
                    <span>Бассейн {settings.poolLengthMeters} м</span>
                  ) : null}

                  {gender ? <span>{gender}</span> : null}

                  {ageGroup ? <span>{ageGroup}</span> : null}

                  {discipline.participantLimit !== null ? (
                    <span>Лимит: {discipline.participantLimit}</span>
                  ) : null}
                </div>

                <div className="discipline-footer">
                  <span>
                    Формат: {discipline.competitionFormat === 'INDIVIDUAL'
                      ? 'личный'
                      : 'командный'}
                  </span>

                  <div className="discipline-actions">
                    <Link
                      className="button button-secondary"
                      to={`/events/${event.id}/disciplines/${discipline.id}/results`}
                    >
                      Результаты
                    </Link>

                    <Link
                      className="button button-primary"
                      to={`/events/${event.publicSlug}/disciplines/${discipline.id}/register`}
                    >
                      Зарегистрироваться
                    </Link>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}