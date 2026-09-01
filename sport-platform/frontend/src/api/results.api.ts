// frontend/src/api/results.api.ts
import { apiFetch } from './http'

export type CompetitionUnitId = string
export type CompetitionUnitEntryId = string

export type CompetitionUnitResponse = {
  id: CompetitionUnitId
  eventDisciplineId: string
  label: string
  sequenceNumber: number | null
  status: string
  scheduledAt: string // ISO‑строка
}

export type AssignRegistrationRequest = {
  registrationId: string
  laneOrPosition: number
}

export type RecordResultRequest = {
  competitionUnitEntryId: CompetitionUnitEntryId
  rawValue: string
}

export type ResultResponse = {
  id: string
  competitionUnitEntryId: CompetitionUnitEntryId
  rawValue: string
  resultType: string
  status: string
  finalPlace: number | null
}

export type CompetitionUnitEntryView = {
  entryId: string
  registrationId: string
  laneOrPosition: number | null
  rawValue: string | null
  resultType: string | null
  resultStatus: string | null
  finalPlace: number | null
}

export type CompetitionUnitDetailsResponse = {
  id: CompetitionUnitId
  eventDisciplineId: string
  label: string
  status: string
  entries: CompetitionUnitEntryView[]
}

export async function getCompetitionUnitDetails(
  unitId: CompetitionUnitId,
  accessToken: string,
): Promise<CompetitionUnitDetailsResponse> {
  return apiFetch<CompetitionUnitDetailsResponse>(
    `/api/v1/competition-units/${unitId}`,
    { accessToken },
  )
}

/**
 * Создать стартовый протокол (competition unit).
 * Доступен только PLATFORM_ADMIN/ORGANIZER на бэкенде.
 */
export async function createCompetitionUnit(
  payload: {
    eventDisciplineId: string
    label: string
    sequenceNumber?: number | null
    scheduledAt?: string | null
  },
  accessToken: string,
): Promise<CompetitionUnitResponse> {
  return apiFetch<CompetitionUnitResponse>('/api/v1/competition-units', {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

/**
 * Привязать заявку к competition unit (задать дорожку/позицию).
 * Доступен только PLATFORM_ADMIN/ORGANIZER.
 */
export async function assignRegistrationToUnit(
  unitId: CompetitionUnitId,
  payload: AssignRegistrationRequest,
  accessToken: string,
): Promise<string> {
  return apiFetch<string>(`/api/v1/competition-units/${unitId}/entries`, {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

/**
 * Записать/обновить результат по конкретной записи протокола.
 * Доступен PLATFORM_ADMIN/OPERATOR.
 */
export async function recordResult(
  payload: RecordResultRequest,
  options: {
    resultType: string
    accessToken: string
  },
): Promise<ResultResponse> {
  const { resultType, accessToken } = options

  const params = new URLSearchParams({ resultType })

  return apiFetch<ResultResponse>(`/api/v1/results?${params.toString()}`, {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

/**
 * Пересчитать места в рамках одного competition unit.
 * Доступен PLATFORM_ADMIN/OPERATOR.
 */
export async function recalculatePlaces(
  unitId: CompetitionUnitId,
  options: {
    rankingStrategy: string
    accessToken: string,
  },
): Promise<void> {
  const { rankingStrategy, accessToken } = options

  const params = new URLSearchParams({ rankingStrategy })

  return apiFetch<void>(
    `/api/v1/competition-units/${unitId}/recalculate?${params.toString()}`,
    {
      method: 'POST',
      accessToken,
    },
  )
}