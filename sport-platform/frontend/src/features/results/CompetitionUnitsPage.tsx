import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import {
  getCompetitionUnitsByDiscipline,
  type CompetitionUnitResponse,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр результатов.'
    }

    if (error.status === 404) {
      return 'Дисциплина не найдена.'
    }

    return error.message
  }

  return 'Не удалось загрузить соревновательные единицы.'
}

function unitStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
  }

  return labels[status] ?? status
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Не назначено'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

export function CompetitionUnitsPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const accessToken = session?.accessToken ?? null

  const [eventDisciplineId, setEventDisciplineId] = useState('')
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const normalizedDisciplineId = eventDisciplineId.trim()

    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    if (!normalizedDisciplineId) {
      setErrorMessage('Введите ID дисциплины.')
      return
    }

    setIsLoading(true)
    setHasLoaded(false)
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
    <section className="results-section">
      <Link className="back-link" to="/operator/payments">
        ← К панели оператора
      </Link>

      <p className="eyebrow">Оператор</p>
      <h1>Результаты</h1>

      <p className="page-description">
        Введите идентификатор дисциплины, чтобы открыть список заплывов,
        матчей, групп или финалов.
      </p>

      <form className="competition-units-search" onSubmit={handleSubmit}>
        <label>
          <span>ID дисциплины</span>

          <input
            type="text"
            value={eventDisciplineId}
            onChange={(event) => setEventDisciplineId(event.target.value)}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            disabled={isLoading}
          />
        </label>

        <button
          className="button button-primary"
          type="submit"
          disabled={isLoading}
        >
          {isLoading ? 'Загружаем…' : 'Открыть'}
        </button>
      </form>

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {hasLoaded && units.length === 0 ? (
        <div className="state-card">
          <h2>Соревновательных единиц пока нет</h2>
          <p>
            Организатор должен создать заплыв, матч, группу или финал
            для этой дисциплины.
          </p>
        </div>
      ) : null}

      {units.length > 0 ? (
        <div className="competition-units-list">
          {units.map((unit) => (
            <article className="competition-unit-card" key={unit.id}>
              <div>
                <p className="discipline-label">
                  Единица #{unit.sequenceNumber ?? '—'}
                </p>

                <h2>{unit.label}</h2>

                <p className="competition-unit-meta">
                  Проведение: {formatDate(unit.scheduledAt)}
                </p>
              </div>

              <div className="competition-unit-actions">
                <span
                  className={`status-badge status-${unit.status.toLowerCase()}`}
                >
                  {unitStatusLabel(unit.status)}
                </span>

                <Link
                  className="button button-secondary"
                  to={`/operator/competition-units/${unit.id}`}
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