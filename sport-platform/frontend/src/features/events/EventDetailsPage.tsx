import { formatVenueDate, venueTimeZoneLabel } from '../../lib/venue-time.mjs'
import { competitionFormatLabel } from '../../lib/discipline-labels.mjs'
// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  getPublicEventDetails,
  type PublicEventDetails,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import styles from './EventDetailsPage.module.css'

type DisciplineSettings = {
  gender?: string
  ageGroup?: string
  distanceMeters?: number
  poolLengthMeters?: number
}


function formatMoney(amount: number | null, currency: string | null) {
  if (amount === null || currency === null || !currency.trim()) {
    return 'Бесплатно'
  }

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

    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      Array.isArray(parsed)
    ) {
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
  const { eventCode } = useParams<{ eventCode: string }>()
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
    <section className={styles.page}>
      <Link className={styles.backLink} to="/">
        ← Все события
      </Link>

      <p className={styles.eyebrow}>Соревнование</p>

      <h1 className={styles.title}>{event.title}</h1>

      {event.description ? (
        <p className={styles.description}>{event.description}</p>
      ) : null}

      <p>{venueTimeZoneLabel(event.venueTimezone)}</p>
      <div className={styles.metaGrid}>
        <div className={styles.metaCard}>
          <span>Регистрация</span>
          <strong>
            {formatVenueDate(event.registrationOpenAt, event.venueTimezone)} —{' '}
            {formatVenueDate(event.registrationCloseAt, event.venueTimezone)}
          </strong>
        </div>

        <div className={styles.metaCard}>
          <span>Дата события</span>
          <strong>
            {formatVenueDate(event.eventStartAt, event.venueTimezone)} — {formatVenueDate(event.eventEndAt, event.venueTimezone)}
          </strong>
        </div>
      </div>

      <div className={styles.sectionHeading}>
        <p className={styles.eyebrow}>Выбор дисциплины</p>
        <h2 className={styles.sectionTitle}>Доступные дисциплины</h2>
      </div>

      {disciplines.length === 0 ? (
        <div className={styles.emptyState}>
          Для этого события пока нет доступных дисциплин.
        </div>
      ) : (
        <div className={styles.disciplineList}>
          {disciplines.map((discipline) => {
            const settings = parseSettings(discipline.settingsJson)
            const gender = getGenderLabel(settings.gender)
            const ageGroup = getAgeGroupLabel(settings.ageGroup)

            return (
              <article className={styles.disciplineCard} key={discipline.id}>
                <div className={styles.disciplineCardTop}>
                  <div>
                    <p className={styles.disciplineLabel}>Дисциплина</p>
                    <h3 className={styles.disciplineTitle}>
                      {formatHeatLabel(discipline.name)}
                    </h3>
                  </div>

                  <strong className={styles.disciplinePrice}>
                    {formatMoney(
                      discipline.entryFeeAmount,
                      discipline.entryFeeCurrency,
                    )}
                  </strong>
                </div>

                <div className={styles.facts}>
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

                <div className={styles.disciplineFooter}>
                  <span>
                    Формат:{' '}
                    {competitionFormatLabel(discipline.competitionFormat)}
                  </span>

                  <div className={styles.actions}>
                    <Link
                      className={styles.secondaryButton}
                      to={`/events/${encodeURIComponent(event.publicSlug)}/disciplines/${encodeURIComponent(discipline.id)}/results`}
                    >
                      Результаты по заплывам
                    </Link>

                    <Link
                      className={styles.secondaryButton}
                      to={`/events/${encodeURIComponent(event.publicSlug)}/disciplines/${encodeURIComponent(discipline.id)}/standings`}
                    >
                      Общий и категорийный зачёт
                    </Link>

                    <Link
                      className={styles.primaryButton}
                      to={`/events/${encodeURIComponent(event.publicSlug)}/disciplines/${encodeURIComponent(discipline.id)}/register`}
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