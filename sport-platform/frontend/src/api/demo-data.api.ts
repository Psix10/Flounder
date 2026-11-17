import { apiFetch } from './http'

export type DemoRow = {
  ordinal: number
  name: string
  gender: string
  seedTimeSeconds: number | null
  registrationId: string | null
}
export type DemoReceipt = {
  requestId: string
  eventId: string
  disciplineId: string
  ruleVersionId: string
  capacity: number
  count: number
  datasetVersion: string
  createdAt: string
  participants: DemoRow[]
}
export type DemoPlan = {
  eventId: string
  disciplineId: string
  ruleVersionId: string
  capacity: number
  count: number
  datasetVersion: string
  state: 'AVAILABLE' | 'PREPARED' | 'BLOCKED'
  message: string
  receipt: DemoReceipt | null
  participants: DemoRow[]
}
export type DemoRequest = {
  requestId: string
  expectedRuleVersionId: string
  expectedCapacity: number
  expectedCount: number
  datasetVersion: string
  confirmed: boolean
}
function path(eventId: string, disciplineId: string) {
  return `/api/v1/events/${encodeURIComponent(eventId)}/disciplines/${encodeURIComponent(disciplineId)}/demo-registrations`
}
export function getDemoPlan(eventId: string, disciplineId: string, token: string) {
  return apiFetch<DemoPlan>(path(eventId, disciplineId), { accessToken: token })
}
export function createDemoRegistrations(eventId: string, disciplineId: string, request: DemoRequest, token: string) {
  return apiFetch<DemoReceipt>(path(eventId, disciplineId), {
    method: 'POST', accessToken: token, body: JSON.stringify(request),
  })
}
