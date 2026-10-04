import { apiFetch } from './http'

export type OrganizationResponse = {
  id: string
  type: string
  name: string
  legalName: string | null
  inn: string | null
  contactEmail: string | null
  contactPhone: string | null
  createdAt: string
  updatedAt: string
}

export async function getOrganizations(
  accessToken: string,
): Promise<OrganizationResponse[]> {
  return apiFetch<OrganizationResponse[]>('/api/v1/organizations', {
    accessToken,
  })
}