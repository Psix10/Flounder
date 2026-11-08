import { apiFetch } from './http'

export type ProtocolRuleBlock = {
  id?: string
  orderIndex: number
  executorCode: string
  config: Record<string, unknown>
}

export type ProtocolRuleSet = {
  id: string
  sportId: string
  code: string
  name: string
  competitionFormat: string
  unitType: string
  ruleSetVersionId: string
  versionNo: number
  status: string
  blocks: ProtocolRuleBlock[]
  createdAt: string
  publishedAt: string | null
}

export type CreateProtocolRuleSet = {
  sportId: string
  code: string
  name: string
  competitionFormat: string
  unitType: string
  blocks: ProtocolRuleBlock[]
}

export type ProtocolAssignment = {
  registrationId: string
  laneOrPosition: number | null
  seedMetric: number | null
  categoryCode: string | null
  seedingGroupCode: string | null
}

export type StartProtocol = {
  eventDisciplineId: string
  unitCount: number
  assignmentCount: number
  units: {
    competitionUnitId: string
    label: string
    sequenceNumber: number | null
    assignments: ProtocolAssignment[]
  }[]
}

export function getCompatibleProtocolRules(id: string, accessToken: string) {
  return apiFetch<ProtocolRuleSet[]>(
    `/api/v1/event-disciplines/${encodeURIComponent(id)}/protocol-rule-versions`,
    { accessToken },
  )
}

export function createProtocolRuleSet(request: CreateProtocolRuleSet, accessToken: string) {
  return apiFetch<ProtocolRuleSet>('/api/v1/protocol-rule-sets', {
    method: 'POST', accessToken, body: JSON.stringify(request),
  })
}

export function publishProtocolRuleSet(rule: ProtocolRuleSet, accessToken: string) {
  return apiFetch<ProtocolRuleSet>(
    `/api/v1/protocol-rule-sets/${encodeURIComponent(rule.id)}/versions/${encodeURIComponent(rule.ruleSetVersionId)}/publish`,
    { method: 'POST', accessToken },
  )
}

export function getStartProtocol(id: string, accessToken: string) {
  return apiFetch<StartProtocol>(
    `/api/v1/event-disciplines/${encodeURIComponent(id)}/start-protocol`,
    { accessToken },
  )
}

export function generateStartProtocol(
  id: string, ruleSetVersionId: string, regenerate: boolean, accessToken: string,
) {
  return apiFetch<StartProtocol>(
    `/api/v1/event-disciplines/${encodeURIComponent(id)}/start-protocol/generate`,
    { method: 'POST', accessToken, body: JSON.stringify({ ruleSetVersionId, regenerate }) },
  )
}



export type DisciplineRegulationConfiguration = {
  regulationVersionId: string
  name: string
  versionNo: number
  ruleSetVersionId: string | null
  settings: import('./regulations.api').ProtocolSettings | null
}

export function getDisciplineRegulationConfiguration(id: string, accessToken: string) {
  return apiFetch<DisciplineRegulationConfiguration>(
    `/api/v1/event-disciplines/${encodeURIComponent(id)}/regulation-configuration`, { accessToken },
  )
}
