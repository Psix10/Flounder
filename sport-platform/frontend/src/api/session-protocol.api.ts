import { apiFetch } from './http'
import type { ProtocolAssignment } from './protocol.api'

export type SessionProtocolUnit = {
  competitionUnitId: string
  label: string
  sequenceNumber: number
  assignments: ProtocolAssignment[]
}

export type SessionProtocolDiscipline = {
  eventDisciplineId: string
  units: SessionProtocolUnit[]
}

export type SessionProtocolHeat = {
  temporaryHeatId: string
  savedHeatId: string
  disciplineId: string
  sessionId: string
  sequenceNumber: number
  start: string
  end: string
}

export type SessionProtocol = {
  snapshotId: string
  eventId: string
  revisionNo: number
  disciplines: SessionProtocolDiscipline[]
  schedule: SessionProtocolHeat[]
}

export type SessionProtocolWindow = {
  start: string
  end: string
}

export type GenerateSessionProtocolRequest = {
  regenerate: boolean
  timingPolicy: {
    minimumRestSeconds: number
    changeoverSeconds: number
    sourceDescription: string
  }
  sessions: {
    id: string
    start: string
    end: string
    breaks: SessionProtocolWindow[]
  }[]
  disciplines: {
    disciplineId: string
    ruleSetVersionId: string | null
    sessionId: string
    heatDurationSeconds: number
    durationBasis: string
  }[]
}

export function getSessionProtocol(
  eventId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<SessionProtocol> {
  return apiFetch<SessionProtocol>(
    `/api/v1/events/${encodeURIComponent(eventId)}/session-protocol`,
    { accessToken, signal },
  )
}

export function generateSessionProtocol(
  eventId: string,
  request: GenerateSessionProtocolRequest,
  accessToken: string,
): Promise<SessionProtocol> {
  return apiFetch<SessionProtocol>(
    `/api/v1/events/${encodeURIComponent(eventId)}/session-protocol/generate`,
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify(request),
    },
  )
}