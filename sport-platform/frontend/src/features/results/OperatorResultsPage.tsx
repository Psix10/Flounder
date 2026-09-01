// frontend/src/features/results/OperatorResultsPage.tsx
import { FormEvent, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  recordResult,
  recalculatePlaces,
  type RecordResultRequest,
} from '../../api/results.api'

const DEFAULT_RESULT_TYPE = 'time_seconds'      // подправишь под свои типы
const DEFAULT_RANKING_STRATEGY = 'ascending'   // подправишь под свои стратегии

function getResultErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'Не удалось сохранить результат. Попробуйте ещё раз.'
  }

  const knownMessages: Record<string, string> = {
    // пример маппинга бизнес‑ошибок, когда появятся коды
    // 'results.invalid_value': 'Некорректный формат результата.',
  }

  return knownMessages[error.code ?? ''] ?? error.message
}

function getRecalculateErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'Не удалось пересчитать результаты. Попробуйте ещё раз.'
  }

  const knownMessages: Record<string, string> = {
    // 'results.cannot_recalculate': 'Сначала введите результаты для всех участников.',
  }

  return knownMessages[error.code ?? ''] ?? error.message
}

export function OperatorResultsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken ?? null

  const [entryId, setEntryId] = useState('')
  const [rawValue, setRawValue] = useState('')
  const [resultType, setResultType] = useState(DEFAULT_RESULT_TYPE)
  const [unitId, setUnitId] = useState('')
  const [rankingStrategy, setRankingStrategy] = useState(DEFAULT_RANKING_STRATEGY)

  const [resultError, setResultError] = useState<string | null>(null)
  const [recalculateError, setRecalculateError] = useState<string | null>(null)
  const [resultSuccess, setResultSuccess] = useState<string | null>(null)
  const [recalculateSuccess, setRecalculateSuccess] = useState<string | null>(null)

  const [isSavingResult, setIsSavingResult] = useState(false)
  const [isRecalculating, setIsRecalculating] = useState(false)

  if (!accessToken) {
    // ProtectedRoute + RequireRole не пустят сюда без сессии, но на всякий случай
    return null
  }

  async function handleResultSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setResultError(null)
    setResultSuccess(null)

    if (!entryId.trim() || !rawValue.trim()) {
      setResultError('Укажите ID записи протокола и результат.')
      return
    }

    setIsSavingResult(true)

    const payload: RecordResultRequest = {
      competitionUnitEntryId: entryId.trim(),
      rawValue: rawValue.trim(),
    }

    try {
      const result = await recordResult(payload, {
        resultType,
        accessToken,
      })
      setResultSuccess(
        `Результат сохранён. Итоговое место: ${
          result.finalPlace ?? 'ещё не присвоено'
        }`,
      )
    } catch (error) {
      setResultError(getResultErrorMessage(error))
    } finally {
      setIsSavingResult(false)
    }
  }

  async function handleRecalculateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRecalculateError(null)
    setRecalculateSuccess(null)

    if (!unitId.trim()) {
      setRecalculateError('Укажите ID competition unit.')
      return
    }

    setIsRecalculating(true)

    try {
      await recalculatePlaces(unitId.trim(), {
        rankingStrategy,
        accessToken,
      })
      setRecalculateSuccess('Пересчёт завершён успешно.')
    } catch (error) {
      setRecalculateError(getRecalculateErrorMessage(error))
    } finally {
      setIsRecalculating(false)
    }
  }

  return (
    <section className="registration-section">
      <div className="auth-card registration-card">
        <p className="eyebrow">Судейская панель</p>
        <h1>Ввод результатов</h1>

        <p className="page-description">
          Введите результат для записи стартового протокола и сохраните его.
        </p>

        <form className="auth-form" onSubmit={handleResultSubmit}>
          <label>
            <span>ID записи протокола (competitionUnitEntryId)</span>
            <input
              disabled={isSavingResult}
              value={entryId}
              onChange={(event) => setEntryId(event.target.value)}
              placeholder="00000000-0000-0000-0000-000000000000"
              required
            />
          </label>

          <label>
            <span>Результат (rawValue)</span>
            <input
              disabled={isSavingResult}
              value={rawValue}
              onChange={(event) => setRawValue(event.target.value)}
              placeholder="Например: 1:05.32"
              required
            />
          </label>

          <label>
            <span>Тип результата (resultType)</span>
            <input
              disabled={isSavingResult}
              value={resultType}
              onChange={(event) => setResultType(event.target.value)}
              placeholder={DEFAULT_RESULT_TYPE}
              required
            />
          </label>

          {resultError ? (
            <p className="form-error" role="alert">
              {resultError}
            </p>
          ) : null}

          {resultSuccess ? (
            <p className="form-success" role="status">
              {resultSuccess}
            </p>
          ) : null}

          <button
            className="button button-primary"
            disabled={isSavingResult}
            type="submit"
          >
            {isSavingResult ? 'Сохраняем…' : 'Сохранить результат'}
          </button>
        </form>
      </div>

      <div className="auth-card registration-card" style={{ marginTop: '2rem' }}>
        <p className="eyebrow">Пересчёт</p>
        <h2>Подтвердить и пересчитать</h2>

        <p className="page-description">
          Укажите competition unit и стратегию ранжирования, чтобы пересчитать
          итоговые места.
        </p>

        <form className="auth-form" onSubmit={handleRecalculateSubmit}>
          <label>
            <span>ID competition unit</span>
            <input
              disabled={isRecalculating}
              value={unitId}
              onChange={(event) => setUnitId(event.target.value)}
              placeholder="00000000-0000-0000-0000-000000000000"
              required
            />
          </label>

          <label>
            <span>Стратегия ранжирования (rankingStrategy)</span>
            <input
              disabled={isRecalculating}
              value={rankingStrategy}
              onChange={(event) => setRankingStrategy(event.target.value)}
              placeholder={DEFAULT_RANKING_STRATEGY}
              required
            />
          </label>

          {recalculateError ? (
            <p className="form-error" role="alert">
              {recalculateError}
            </p>
          ) : null}

          {recalculateSuccess ? (
            <p className="form-success" role="status">
              {recalculateSuccess}
            </p>
          ) : null}

          <button
            className="button button-secondary"
            disabled={isRecalculating}
            type="submit"
          >
            {isRecalculating ? 'Пересчитываем…' : 'Подтвердить и пересчитать'}
          </button>
        </form>
      </div>
    </section>
  )
}