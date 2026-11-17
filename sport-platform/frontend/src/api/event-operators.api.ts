import { apiFetch } from './http'
import { normalizeOperatorEmail, validateOperatorCandidates, validateOperatorMembers } from '../lib/event-operators.mjs'

export type EventOperatorMember = {
  id: string; eventId: string; operatorUserId: string; assignedByUserId: string;
  createdAt: string; email: string | null; displayName: string; eligible: boolean;
}
export type EventOperatorCandidate = {
  userId: string; email: string; displayName: string; assigned: boolean;
}
function base(eventId: string) {
  return `/api/v1/events/${encodeURIComponent(eventId)}/operators`
}
export async function getEventOperatorMembers(eventId: string, accessToken: string): Promise<EventOperatorMember[]> {
  const value = await apiFetch<unknown>(`${base(eventId)}/members`, { accessToken })
  return validateOperatorMembers(value, eventId)
}
export async function findEventOperator(eventId: string, email: string, accessToken: string): Promise<EventOperatorCandidate[]> {
  const normalized = normalizeOperatorEmail(email)
  const query = new URLSearchParams({ email: normalized })
  const value = await apiFetch<unknown>(`${base(eventId)}/candidates?${query}`, { accessToken })
  return validateOperatorCandidates(value, normalized)
}
export async function assignEventOperator(eventId: string, userId: string, accessToken: string): Promise<void> {
  await apiFetch<void>(`${base(eventId)}/${encodeURIComponent(userId)}`, { method: 'PUT', accessToken })
}
export async function removeEventOperator(eventId: string, userId: string, accessToken: string): Promise<void> {
  await apiFetch<void>(`${base(eventId)}/${encodeURIComponent(userId)}`, { method: 'DELETE', accessToken })
}
