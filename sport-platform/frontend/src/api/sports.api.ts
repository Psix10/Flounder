import { apiFetch } from './http'

export type SportResponse = {
  id: string
  code: string
  name: string
  isActive: boolean
}

export type Sport = SportResponse

export async function getSports(accessToken: string): Promise<SportResponse[]> {
  return apiFetch<SportResponse[]>('/api/v1/sports', { accessToken })
}



export type DisciplineTemplateResponse = {
  id: string
  sportId: string
  code: string
  name: string
  competitionFormat: string
  unitType: string
  resultType: string
  rankingStrategy: string
  defaultMeta?: string | null
}

export async function getDisciplineTemplates(
  accessToken: string,
): Promise<DisciplineTemplateResponse[]> {
  return apiFetch<DisciplineTemplateResponse[]>(
    '/api/v1/discipline-templates',
    { accessToken },
  )
}