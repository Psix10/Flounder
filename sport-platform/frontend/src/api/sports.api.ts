import { apiFetch } from './http'

export type Sport = {
  id: string
  code: string
  name: string
  active: boolean
}

export function getSports(accessToken: string): Promise<Sport[]> {
  return apiFetch<Sport[]>('/api/v1/sports', {
    accessToken,
  })
}