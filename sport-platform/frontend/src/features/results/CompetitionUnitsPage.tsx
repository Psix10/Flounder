// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { ApiError } from '../../api/http'
import {
  getCompetitionUnitsByDiscipline,
  type CompetitionUnitResponse,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './CompetitionUnitsPage.module.css'

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите в систему снова.'
    }

    if (error.status === 403) {
      return 'Нет прав на просмотр результатов этой дисциплины.'
    }

    if (error.status === 404) {
      return 'Дисциплина не найдена.'
    }

    return error.message || 'Не удалось загрузить заплывы.'
  }

  return 'Не удалось загрузить заплывы.'
}

function unitStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
  }

  return labels[status] ?? status
}

function unitStatusClassName(status: string): string {
  const classes: Record<string, string> = {
    DRAFT: styles.statusDraft,
    PUBLISHED: styles.statusPublished,
  }

  return `${styles.statusBadge} ${
    classes[status] ?? styles.statusNeutral
  }`
}

function formatDate(value: string | null): string {
  if (!value) {
    return 'Не назначено'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'Не назначено'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(date)
}

export function CompetitionUnitsPage() {
  const [searchParams] = useSearchParams()
  const { session } = useAuth()

  const sourceDisciplineId = searchParams.get('disciplineId') ?? ''
  const sourceEventId = searchParams.get('eventId') ?? ''

  return (
    <CompetitionUnitsContent
      key={`${sourceEventId}:${sourceDisciplineId}:${
        session?.accessToken ?? ''
      }`}
      initialDisciplineId={sourceDisciplineId}
      sourceEventId={sourceEventId}
      accessToken={session?.accessToken}
    />
  )
}

type CompetitionUnitsContentProps = {
  initialDisciplineId: string
  sourceEventId: string
  accessToken?: string
}

function CompetitionUnitsContent({
  initialDisciplineId,
  sourceEventId,
  accessToken,
}: CompetitionUnitsContentProps) {
  const navigate = useNavigate()

  const [eventDisciplineId, setEventDisciplineId] = useState(
    initialDisciplineId,
  )
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (isLoading) {
      return
    }

    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    const normalizedDisciplineId = eventDisciplineId.trim()

    if (!normalizedDisciplineId) {
      setUnits([])
      setHasLoaded(false)
      setErrorMessage('Введите ID дисциплины.')
      return
    }

    setIsLoading(true)
    setHasLoaded(false)
    setUnits([])
    setErrorMessage(null)

    try {
      const data = await getCompetitionUnitsByDiscipline(
        normalizedDisciplineId,
        accessToken,
      )

      setUnits(data)
      setHasLoaded(true)
    } catch (error) {
      setUnits([])
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link
        className={styles.backLink}
        to={
          sourceEventId
            ? `/organizer/events/${encodeURIComponent(sourceEventId)}`
            : '/'
        }
      >
        {sourceEventId ? '← К мероприятию' : '← На главную'}
      </Link>

      <p className={styles.eyebrow}>Управление результатами</p>

      <h1 className={styles.title}>Заплывы и результаты</h1>

      <p className={styles.description}>
        {initialDisciplineId
          ? 'Дисциплина уже выбрана. Нажмите «Открыть», затем выберите нужный заплыв.'
          : 'Введите идентификатор дисциплины, чтобы открыть список заплывов, матчей, групп или финалов.'}
      </p>

      <form className={styles.searchForm} onSubmit={handleSubmit}>
        <label className={styles.field}>
          <span>ID дисциплины</span>

          <input
            autoComplete="off"
            disabled={isLoading}
            onChange={(event) => {
              setEventDisciplineId(event.target.value)
              setUnits([])
              setHasLoaded(false)
              setErrorMessage(null)
            }}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            spellCheck={false}
            type="text"
            value={eventDisciplineId}
          />
        </label>

        <button
          className={styles.submitButton}
          disabled={isLoading || !eventDisciplineId.trim()}
          type="submit"
        >
          {isLoading ? 'Загружаем…' : 'Открыть'}
        </button>
      </form>

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {isLoading ? (
        <div className={styles.stateCard} role="status">
          Загружаем заплывы…
        </div>
      ) : null}

      {hasLoaded && units.length === 0 ? (
        <div className={styles.stateCard}>
          <h2>Соревновательных единиц пока нет</h2>
          <p>
            Для выбранной дисциплины ещё не созданы заплывы, матчи,
            группы или финалы.
          </p>
        </div>
      ) : null}

      {units.length > 0 ? (
        <div className={styles.unitsList}>
          {units.map((unit) => (
            <article className={styles.unitCard} key={unit.id}>
              <div>
                <p className={styles.unitLabel}>
                  Заплыв № {unit.sequenceNumber ?? '—'}
                </p>

                <h2 className={styles.unitTitle}>{formatHeatLabel(unit.label)}</h2>

                <p className={styles.unitMeta}>
                  Проведение: {formatDate(unit.scheduledAt)}
                </p>
              </div>

              <div className={styles.unitActions}>
                <span className={unitStatusClassName(unit.status)}>
                  {unitStatusLabel(unit.status)}
                </span>

                <Link
                  className={styles.openLink}
                  to={{
                    pathname: `/operator/competition-units/${encodeURIComponent(unit.id)}`,
                    search: sourceEventId
                      ? `?${new URLSearchParams({
                          eventId: sourceEventId,
                          disciplineId: unit.eventDisciplineId,
                        }).toString()}`
                      : '',
                  }}
                >
                  Открыть результаты
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}