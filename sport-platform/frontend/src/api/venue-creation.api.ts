import { apiFetch } from './http'
import type { VenueResponse } from './venues.api'

export type CreateVenueRequest = {
  name: string
  countryCode: string | null
  city: string | null
  address: string | null
  timezone: string
}

export function createVenue(
  request: CreateVenueRequest,
  accessToken: string,
): Promise<VenueResponse> {
  return apiFetch<VenueResponse>('/api/v1/venues', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(request),
  })
}