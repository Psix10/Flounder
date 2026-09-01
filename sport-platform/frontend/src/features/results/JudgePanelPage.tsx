import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getCompetitionUnitDetails,
  recordResult,
  recalculatePlaces,
  type CompetitionUnitDetailsResponse,
} from '../../api/results.api'

const DEFAULT_RESULT_TYPE = 'time_seconds'
const DEFAULT_RANKING_STRATEGY = 'ascending'

export function JudgePanelPage() {
  const { unitId } = useParams<{ unitId: string }>()
  const { session } = useAuth()
  const accessToken = session?.accessToken ?? null

  const [unit, setUnit] = useState<CompetitionUnitDetailsResponse | null>(null)
  const [draftValues, setDraftValues] = useState<Record<string, string>>({})
  const [resultType, setResultType] = useState(DEFAULT_RESULT_TYPE)
  const [rankingStrategy, setRankingStrategy] = useState(DEFAULT_RANKING_STRATEGY)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [recalculating, setRecalculating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function loadUnit() {
    if (!unitId || !accessToken) return
    setLoading(true)
    setError(null)
    try {
      const details = await getCompetitionUnitDetails(unitId, accessToken)
      setUnit(details)
      const initialDrafts: Record<string, string> = {}
      details.entries.forEach((entry) => {
        initialDrafts[entry.entryId] = entry.rawValue ?? ''
      })
      setDraftValues(initialDrafts)
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : 'Не удалось загрузить протокол.',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUnit()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unitId, accessToken])

  if (!unitId || !accessToken) return null
  if (loading) return <div>Загрузка протокола…</div>
  if (error) return <div className="form-error">{error}</div>
  if (!unit) return <div>Протокол не найден</div>

  async function handleSaveAndRecalculate() {
    setSaving(true)
    setError(null)

    try {
      for (const entry of unit!.entries) {
        const value = draftValues[entry.entryId]?.trim()
        if (!value) continue

        await recordResult(
          {
            competitionUnitEntryId: entry.entryId,
            rawValue: value,
          },
          { resultType, accessToken },
        )
      }

      setRecalculating(true)
      await recalculatePlaces(unit!.id, { rankingStrategy, accessToken })
      await loadUnit()
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : 'Ошибка при сохранении результатов.',
      )
    } finally {
      setSaving(false)
      setRecalculating(false)
    }
  }

  return (
    <div className="registration-section">
      <div className="auth-card registration-card">
        <p className="eyebrow">Судейская панель</p>
        <h1>{unit.label}</h1>
        <p className="page-description">Статус: {unit.status}</p>

        <label>
          <span>Тип результата (resultType)</span>
          <input
            value={resultType}
            onChange={(e) => setResultType(e.target.value)}
            disabled={saving}
          />
        </label>

        <table style={{ width: '100%', marginTop: '1rem' }}>
          <thead>
            <tr>
              <th>Регистрация</th>
              <th>Дорожка</th>
              <th>Результат</th>
              <th>Место</th>
            </tr>
          </thead>
          <tbody>
            {unit.entries.map((entry) => (
              <tr key={entry.entryId}>
                <td>{entry.registrationId}</td>
                <td>{entry.laneOrPosition ?? '-'}</td>
                <td>
                  <input
                    value={draftValues[entry.entryId] ?? ''}
                    onChange={(e) =>
                      setDraftValues((prev) => ({
                        ...prev,
                        [entry.entryId]: e.target.value,
                      }))
                    }
                    disabled={saving}
                    placeholder="Например: 1:05.32"
                  />
                </td>
                <td>{entry.finalPlace ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <label style={{ marginTop: '1rem' }}>
          <span>Стратегия ранжирования (rankingStrategy)</span>
          <input
            value={rankingStrategy}
            onChange={(e) => setRankingStrategy(e.target.value)}
            disabled={recalculating}
          />
        </label>

        {error && <p className="form-error">{error}</p>}

        <button
          className="button button-primary"
          onClick={handleSaveAndRecalculate}
          disabled={saving}
          style={{ marginTop: '1rem' }}
        >
          {saving ? 'Сохраняю…' : 'Подтвердить и пересчитать'}
        </button>
      </div>
    </div>
  )
}