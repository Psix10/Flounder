import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  changeCompetitionUnitPublication,
  getCompetitionUnitDetails,
  recalculatePlaces,
  recordResult,
  type CompetitionUnitDetailsResponse,
  type RankingStrategy,
  type ResultType,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'

type EditableResultStatus =
  | 'PENDING'
  | 'DID_NOT_START'
  | 'DID_NOT_FINISH'
  | 'DISQUALIFIED'

const RESULT_STATUS_OPTIONS: Array<{
  value: EditableResultStatus
  label: string
}> = [
  {
    value: 'PENDING',
    label: 'Обычный результат',
  },
  {
    value: 'DID_NOT_START',
    label: 'DNS — не стартовал',
  },
  {
    value: 'DID_NOT_FINISH',
    label: 'DNF — не финишировал',
  },
  {
    value: 'DISQUALIFIED',
    label: 'DSQ — дисквалифицирован',
  },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите в систему снова.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для работы с результатами.'
    }

    if (error.status === 404) {
      return 'Заплыв, матч или соревновательная единица не найдены.'
    }

    return error.message
  }

  return fallback
}

function resultStatusLabel(status: string | null) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта',
    VALID: 'Учтён',
    DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал',
    DISQUALIFIED: 'Дисквалифицирован',
    CORRECTED: 'Исправлен',
    EXHIBITION: 'Вне зачёта',
  }

  return status ? labels[status] ?? status : 'Нет результата'
}

function unitStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
  }

  return labels[status] ?? status
}

function sortEntries(
  entries: CompetitionUnitDetailsResponse['entries'],
) {
  return [...entries].sort((left, right) => {
    const leftLane = left.laneOrPosition ?? Number.MAX_SAFE_INTEGER
    const rightLane = right.laneOrPosition ?? Number.MAX_SAFE_INTEGER

    return leftLane - rightLane
  })
}

export function JudgePanelPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { unitId } = useParams<{ unitId: string }>()
  const accessToken = session?.accessToken ?? null

  const [unit, setUnit] =
    useState<CompetitionUnitDetailsResponse | null>(null)

  const [draftValues, setDraftValues] =
    useState<Record<string, string>>({})

  const [draftStatuses, setDraftStatuses] =
    useState<Record<string, EditableResultStatus>>({})

  const [resultType, setResultType] =
    useState<ResultType>('TIME')

  const [rankingStrategy, setRankingStrategy] =
    useState<RankingStrategy>('ASC')

  const [isLoading, setIsLoading] = useState(true)

  const [savingEntryId, setSavingEntryId] =
    useState<string | null>(null)

  const [isRecalculating, setIsRecalculating] = useState(false)

  const [isChangingPublication, setIsChangingPublication] =
    useState(false)

  const [errorMessage, setErrorMessage] =
    useState<string | null>(null)

  const [successMessage, setSuccessMessage] =
    useState<string | null>(null)

  const entries = useMemo(
    () => (unit ? sortEntries(unit.entries) : []),
    [unit],
  )

  const isPublished = unit?.status === 'PUBLISHED'

  const hasPendingResults = entries.some(
    (entry) =>
      Boolean(entry.rawValue?.trim())
      && entry.resultStatus === 'PENDING',
  )

  function updateDraftValues(
    nextUnit: CompetitionUnitDetailsResponse,
  ) {
    setDraftValues(
      Object.fromEntries(
        nextUnit.entries.map((entry) => [
          entry.entryId,
          entry.rawValue ?? '',
        ]),
      ),
    )

    setDraftStatuses(
      Object.fromEntries(
        nextUnit.entries.map((entry) => [
          entry.entryId,
          entry.resultStatus === 'DID_NOT_START'
          || entry.resultStatus === 'DID_NOT_FINISH'
          || entry.resultStatus === 'DISQUALIFIED'
            ? entry.resultStatus
            : 'PENDING',
        ]),
      ),
    )
  }

  async function loadUnit() {
    if (!unitId || !accessToken) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const loadedUnit = await getCompetitionUnitDetails(
        unitId,
        accessToken,
      )

      setUnit(loadedUnit)
      updateDraftValues(loadedUnit)
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          'Не удалось загрузить стартовый лист и результаты.',
        ),
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    if (!unitId) {
      setErrorMessage('Идентификатор заплыва или матча не указан.')
      setIsLoading(false)
      return
    }

    void loadUnit()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, unitId, navigate])

  async function handleSaveResult(entryId: string) {
    if (!accessToken || isPublished) {
      return
    }

    const status = draftStatuses[entryId] ?? 'PENDING'
    const rawValue = draftValues[entryId]?.trim()

    if (status === 'PENDING' && !rawValue) {
      setErrorMessage('Введите результат перед сохранением.')
      return
    }

    setSavingEntryId(entryId)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      await recordResult(
        status === 'PENDING'
          ? {
              competitionUnitEntryId: entryId,
              rawValue,
            }
          : {
              competitionUnitEntryId: entryId,
              rawValue: '',
              status,
            },
        {
          resultType,
          accessToken,
        },
      )

      setSuccessMessage(
        status === 'PENDING'
          ? 'Результат сохранён. Пересчитайте места, чтобы обновить таблицу.'
          : `Статус «${resultStatusLabel(status)}» сохранён.`,
      )

      await loadUnit()
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось сохранить результат.'),
      )
    } finally {
      setSavingEntryId(null)
    }
  }

  async function handleRecalculate() {
    if (!unit || !accessToken || isPublished) {
      return
    }

    setIsRecalculating(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updatedUnit = await recalculatePlaces(
        unit.id,
        {
          rankingStrategy,
          accessToken,
        },
      )

      setUnit(updatedUnit)
      updateDraftValues(updatedUnit)

      setSuccessMessage('Места успешно пересчитаны.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось пересчитать места.'),
      )
    } finally {
      setIsRecalculating(false)
    }
  }

  async function handleChangePublication() {
    if (!unit || !accessToken) {
      return
    }

    const willPublish = unit.status !== 'PUBLISHED'

    setIsChangingPublication(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      await changeCompetitionUnitPublication(
        unit.id,
        willPublish,
        accessToken,
      )

      await loadUnit()

      setSuccessMessage(
        willPublish
          ? 'Результаты опубликованы и доступны на публичной странице.'
          : 'Публикация результатов снята.',
      )
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          'Не удалось изменить статус публикации результатов.',
        ),
      )
    } finally {
      setIsChangingPublication(false)
    }
  }

  return (
    <section className="results-section">
      <Link className="back-link" to="/operator/payments">
        ← К панели оператора
      </Link>

      <p className="eyebrow">Судейская панель</p>
      <h1>{unit?.label ?? 'Результаты'}</h1>

      <p className="page-description">
        Вносите результаты участников, затем пересчитывайте итоговые места.
        Для времени используйте формат <code>00:58.42</code>.
      </p>

      {isLoading ? (
        <div className="state-card">
          <h2>Загрузка</h2>
          <p>Получаем стартовый лист и результаты.</p>
        </div>
      ) : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {successMessage ? (
        <p className="form-success" role="status">
          {successMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && unit ? (
        <>
          <dl className="detail-list">
            <div>
              <dt>Статус</dt>
              <dd>{unitStatusLabel(unit.status)}</dd>
            </div>

            <div>
              <dt>Дисциплина</dt>
              <dd>{unit.eventDisciplineId}</dd>
            </div>

            <div>
              <dt>Участников</dt>
              <dd>{unit.entries.length}</dd>
            </div>
          </dl>

          {isPublished ? (
            <p className="results-hint" role="status">
              Результаты опубликованы и защищены от изменений. Снимите
              публикацию, чтобы изменить результат или пересчитать места.
            </p>
          ) : null}

          <div className="results-toolbar">
            <label>
              <span>Тип результата</span>

              <select
                value={resultType}
                onChange={(event) => {
                  const nextType = event.target.value as ResultType

                  setResultType(nextType)
                  setRankingStrategy(
                    nextType === 'TIME' ? 'ASC' : 'DESC',
                  )
                }}
                disabled={
                  isPublished
                  || isRecalculating
                  || isChangingPublication
                }
              >
                <option value="TIME">
                  Время — меньше лучше
                </option>

                <option value="POINTS">
                  Очки — больше лучше
                </option>
              </select>
            </label>

            <button
              className="button button-primary"
              type="button"
              onClick={handleRecalculate}
              disabled={
                isPublished
                || isRecalculating
                || isChangingPublication
                || entries.length === 0
              }
            >
              {isRecalculating
                ? 'Пересчитываем…'
                : 'Пересчитать места'}
            </button>

            <button
              className="button button-secondary"
              type="button"
              onClick={handleChangePublication}
              disabled={
                isChangingPublication
                || isRecalculating
                || entries.length === 0
                || (!isPublished && hasPendingResults)
              }
            >
              {isChangingPublication
                ? 'Обновляем…'
                : isPublished
                  ? 'Снять с публикации'
                  : 'Опубликовать результаты'}
            </button>
          </div>

          {!isPublished && hasPendingResults ? (
            <p className="results-hint">
              Перед публикацией пересчитайте места после внесения
              результатов.
            </p>
          ) : null}

          {entries.length === 0 ? (
            <div className="state-card">
              <h2>Участники не назначены</h2>

              <p>
                Организатор должен добавить подтверждённые заявки в этот
                заплыв или матч.
              </p>
            </div>
          ) : (
            <div className="results-table-wrap">
              <table className="results-table">
                <thead>
                  <tr>
                    <th scope="col">Место</th>
                    <th scope="col">Дорожка</th>
                    <th scope="col">Участник</th>
                    <th scope="col">Результат</th>
                    <th scope="col">Статус ввода</th>
                    <th scope="col">Статус</th>
                    <th scope="col">
                      <span className="visually-hidden">
                        Действия
                      </span>
                    </th>
                  </tr>
                </thead>

                <tbody>
                  {entries.map((entry) => {
                    const isSaving =
                      savingEntryId === entry.entryId

                    const draftStatus =
                      draftStatuses[entry.entryId] ?? 'PENDING'

                    const hasSpecialStatus =
                      draftStatus !== 'PENDING'

                    return (
                      <tr key={entry.entryId}>
                        <td className="results-place">
                          {entry.finalPlace ?? '—'}
                        </td>

                        <td>
                          {entry.laneOrPosition ?? '—'}
                        </td>

                        <td>
                          <strong className="result-participant-name">
                            {entry.participantName}
                          </strong>

                          <span className="results-registration-id">
                            Заявка {entry.registrationId.slice(0, 8)}
                          </span>
                        </td>

                        <td>
                          <input
                            className="result-input"
                            type="text"
                            value={draftValues[entry.entryId] ?? ''}
                            placeholder={
                              hasSpecialStatus
                                ? 'Не требуется'
                                : resultType === 'TIME'
                                  ? '00:58.42'
                                  : '0'
                            }
                            onChange={(event) => {
                              setDraftValues((current) => ({
                                ...current,
                                [entry.entryId]: event.target.value,
                              }))
                            }}
                            disabled={
                              isPublished
                              || hasSpecialStatus
                              || isSaving
                              || isRecalculating
                              || isChangingPublication
                            }
                          />
                        </td>

                        <td>
                          <select
                            className="result-status-select"
                            value={draftStatus}
                            onChange={(event) => {
                              const nextStatus =
                                event.target.value as EditableResultStatus

                              setDraftStatuses((current) => ({
                                ...current,
                                [entry.entryId]: nextStatus,
                              }))

                              if (nextStatus !== 'PENDING') {
                                setDraftValues((current) => ({
                                  ...current,
                                  [entry.entryId]: '',
                                }))
                              }
                            }}
                            disabled={
                              isPublished
                              || isSaving
                              || isRecalculating
                              || isChangingPublication
                            }
                          >
                            {RESULT_STATUS_OPTIONS.map((option) => (
                              <option
                                key={option.value}
                                value={option.value}
                              >
                                {option.label}
                              </option>
                            ))}
                          </select>
                        </td>

                        <td>
                          <span className="status-badge">
                            {resultStatusLabel(entry.resultStatus)}
                          </span>
                        </td>

                        <td>
                          <button
                            className="button button-secondary result-save-button"
                            type="button"
                            onClick={() =>
                              handleSaveResult(entry.entryId)
                            }
                            disabled={
                              isPublished
                              || isSaving
                              || isRecalculating
                              || isChangingPublication
                              || (
                                !hasSpecialStatus
                                && !draftValues[entry.entryId]?.trim()
                              )
                            }
                          >
                            {isSaving
                              ? 'Сохраняем…'
                              : 'Сохранить'}
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : null}
    </section>
  )
}