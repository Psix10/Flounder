import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  getPublicDisciplineResults,
  type PublicDisciplineResultsResponse,
} from '../../api/results.api'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 404) {
    return 'Событие или дисциплина не найдены.'
  }

  if (error instanceof ApiError) {
    return error.message
  }

  return 'Не удалось загрузить результаты.'
}

function resultStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта',
    VALID: 'Учтён',
    DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал',
    DISQUALIFIED: 'Дисквалифицирован',
    CORRECTED: 'Исправлен',
    EXHIBITION: 'Вне зачёта',
  }

  return labels[status] ?? status
}

function displayResult(
  rawValue: string | null,
  status: string,
) {
  if (
    status === 'DID_NOT_START'
    || status === 'DID_NOT_FINISH'
    || status === 'DISQUALIFIED'
  ) {
    return '—'
  }

  return rawValue?.trim() || '—'
}

export function PublicDisciplineResultsPage() {
  const { eventId, disciplineId } = useParams<{
    eventId: string
    disciplineId: string
  }>()

  const [results, setResults] =
    useState<PublicDisciplineResultsResponse | null>(null)

  const [isLoading, setIsLoading] = useState(true)

  const [errorMessage, setErrorMessage] =
    useState<string | null>(null)

  async function loadResults() {
    if (!eventId || !disciplineId) {
      setErrorMessage('Не указан идентификатор события или дисциплины.')
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const data = await getPublicDisciplineResults(
        eventId,
        disciplineId,
      )

      setResults(data)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadResults()
  }, [eventId, disciplineId])

  return (
    <section className="results-section">
      <Link
        className="back-link"
        to={eventId ? `/events/${eventId}` : '/'}
      >
        ← К событию
      </Link>

      <p className="eyebrow">Результаты соревнований</p>
      <h1>Таблица результатов</h1>

      <p className="page-description">
        Здесь показаны только опубликованные результаты дисциплины.
      </p>

      {isLoading ? (
        <div className="state-card">
          <h2>Загрузка</h2>
          <p>Получаем опубликованные результаты дисциплины.</p>
        </div>
      ) : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && results ? (
        results.units.length === 0 ? (
          <div className="state-card">
            <h2>Результаты ещё не опубликованы</h2>
            <p>
              Организатор опубликует итоговую таблицу после завершения
              соревнования.
            </p>
          </div>
        ) : (
          <div className="public-results-list">
            {results.units.map((unit) => (
              <article className="public-results-unit" key={unit.id}>
                <div className="public-results-unit-header">
                  <div>
                    <p className="discipline-label">
                      Соревновательная единица
                    </p>
                    <h2>{unit.label}</h2>
                  </div>

                  {unit.sequenceNumber !== null ? (
                    <span className="status-badge">
                      № {unit.sequenceNumber}
                    </span>
                  ) : null}
                </div>

                {unit.entries.length === 0 ? (
                  <p className="results-hint">
                    В этой соревновательной единице нет опубликованных
                    результатов.
                  </p>
                ) : (
                  <div className="results-table-wrap">
                    <table className="results-table">
                      <thead>
                        <tr>
                          <th scope="col">Место</th>
                          <th scope="col">Участник</th>
                          <th scope="col">Клуб</th>
                          <th scope="col">Результат</th>
                          <th scope="col">Статус</th>
                        </tr>
                      </thead>

                      <tbody>
                        {unit.entries.map((entry, index) => (
                          <tr
                            key={
                              `${unit.id}-${entry.participantName}-${index}`
                            }
                          >
                            <td className="results-place">
                              {entry.place ?? '—'}
                            </td>

                            <td>
                              <strong className="result-participant-name">
                                {entry.participantName}
                              </strong>
                            </td>

                            <td>{entry.clubName ?? '—'}</td>

                            <td>
                              {displayResult(
                                entry.rawValue,
                                entry.status,
                              )}
                            </td>

                            <td>
                              <span className="status-badge">
                                {resultStatusLabel(entry.status)}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </article>
            ))}
          </div>
        )
      ) : null}
    </section>
  )
}