import { apiFetch } from './http'
import { getCompetitionUnitsByDiscipline, type CompetitionUnitResponse } from './results.api'
import { validateOperatorEvents, validateOperatorEvent, validateOperatorDisciplines, validateOperatorUnits } from '../lib/operator-workspace.mjs'

export type OperatorEvent = {
  id: string; title: string; status: string;
  eventStartAt?: string | null; eventEndAt?: string | null; publicSlug?: string | null;
}
export type OperatorDiscipline = {
  id: string; eventId: string; name: string; status: string; competitionFormat: string;
  unitType?: string | null; resultType?: string | null;
}
export async function getOperatorEvents(accessToken: string): Promise<OperatorEvent[]> {
  return validateOperatorEvents(await apiFetch<unknown>('/api/v1/operator/events', { accessToken }))
}
export async function getOperatorEvent(eventId: string, accessToken: string): Promise<OperatorEvent> {
  return validateOperatorEvent(await apiFetch<unknown>(`/api/v1/operator/events/${encodeURIComponent(eventId)}`, { accessToken }), eventId)
}
export async function getOperatorDisciplines(eventId: string, accessToken: string): Promise<OperatorDiscipline[]> {
  return validateOperatorDisciplines(await apiFetch<unknown>(`/api/v1/operator/events/${encodeURIComponent(eventId)}/disciplines`, { accessToken }), eventId)
}
export async function getOperatorUnits(disciplineId: string, accessToken: string): Promise<CompetitionUnitResponse[]> {
  return validateOperatorUnits(await getCompetitionUnitsByDiscipline(disciplineId, accessToken), disciplineId)
}
