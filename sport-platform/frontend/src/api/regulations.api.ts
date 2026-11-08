import { apiFetch } from './http'

export type RegulationRules = {
  sections?: Array<{ code: string; title: string }>
  title?: string
  participation?: {
    minAge?: number | null
    maxAge?: number | null
    requirements?: string[]
  }
  disciplines?: Array<{
    name: string
    description?: string
  }>
  scoring?: {
    description?: string
  }
  additionalConditions?: string[]
}

export type RegulationTemplateResponse = {
  id: string
  sportId: string
  code: string
  name: string
  description: string | null
  isActive: boolean
  createdAt: string
}

export type RegulationVersionResponse = {
  id: string
  regulationTemplateId: string
  versionNo: number
  status: string
  effectiveFrom: string | null
  rulesJson: RegulationRules
  notes: string | null
  createdBy: string | null
  createdAt: string
}

export type CreateRegulationTemplateRequest = {
  sportId: string
  code: string
  name: string
  description: string | null
}

export type CreateRegulationVersionRequest = {
  versionNo: number
  rulesJson: RegulationRules
  notes: string | null
  effectiveFrom: string | null
}

export async function getRegulationTemplates(
  accessToken: string,
): Promise<RegulationTemplateResponse[]> {
  return apiFetch<RegulationTemplateResponse[]>(
    '/api/v1/regulation-templates',
    {
      accessToken,
    },
  )
}

export async function getRegulationVersions(
  accessToken: string,
): Promise<RegulationVersionResponse[]> {
  return apiFetch<RegulationVersionResponse[]>(
    '/api/v1/regulation-versions',
    {
      accessToken,
    },
  )
}

export async function createRegulationTemplate(
  payload: CreateRegulationTemplateRequest,
  accessToken: string,
): Promise<RegulationTemplateResponse> {
  return apiFetch<RegulationTemplateResponse>(
    '/api/v1/regulation-templates',
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify(payload),
    },
  )
}

export async function createRegulationVersion(
  templateId: string,
  payload: CreateRegulationVersionRequest,
  accessToken: string,
): Promise<RegulationVersionResponse> {
  return apiFetch<RegulationVersionResponse>(
    `/api/v1/regulation-templates/${encodeURIComponent(templateId)}/versions`,
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify(payload),
    },
  )
}

export async function publishRegulationVersion(
  versionId: string,
  accessToken: string,
): Promise<RegulationVersionResponse> {
  return apiFetch<RegulationVersionResponse>(
    `/api/v1/regulation-versions/${encodeURIComponent(versionId)}/publish`,
    {
      method: 'POST',
      accessToken,
    },
  )
}


export type ProtocolEligibility = {
  allowedGenders?: Array<'MALE' | 'FEMALE'>
  ageMethod?: 'ON_DATE' | 'BY_YEAR'
  ageReferenceDate?: string
  minAge?: number
  maxAge?: number
}

export type ProtocolCategory = {
  code: string
  seedingGroupCode: string
  eligibility: ProtocolEligibility
}

export type ProtocolSettings = {
  capacity: number
  positionOrder: number[]
  metricKey: string
  heatOrder: 'FASTEST_FIRST' | 'SLOWEST_FIRST'
  partialHeat: 'FIRST' | 'LAST'
  categories?: ProtocolCategory[] | null
}

export type RegulationConfiguration = {
  document: {
    id: string; templateId: string; sportId: string; code: string; name: string
    description: string | null; versionNo: number; status: string
    effectiveFrom: string | null; notes: string | null; createdBy: string | null
    conditions: string[]
    binding: { ruleVersionId: string; competitionFormat: string; unitType: string } | null
  }
  protocol: ProtocolSettings | null
}

export type PublishRegulationConfiguration = {
  sourceVersionId: string | null; sportId: string; code: string; name: string
  description: string | null; versionNo: number; effectiveFrom: string | null
  notes: string | null; conditions: string[]; protocol: ProtocolSettings | null
}

export function getRegulationConfiguration(id: string, accessToken: string) {
  return apiFetch<RegulationConfiguration>(`/api/v1/regulation-configurations/${encodeURIComponent(id)}`, { accessToken })
}

export function publishRegulationConfiguration(request: PublishRegulationConfiguration, accessToken: string) {
  return apiFetch<RegulationConfiguration>('/api/v1/regulation-configurations', {
    method: 'POST', accessToken, body: JSON.stringify(request),
  })
}
