import { apiFetch } from './http'
import type {
  PublicEvent,
  PublicEventDetails,
} from '../features/events/event.types'

export function getPublicEvents(): Promise<PublicEvent[]> {
  return apiFetch<PublicEvent[]>('/api/v1/public/events')
}

export function getPublicEventDetails(
  publicSlug: string,
): Promise<PublicEventDetails> {
  return apiFetch<PublicEventDetails>(
    `/api/v1/public/events/${encodeURIComponent(publicSlug)}`,
  )
}

export type OrganizerEvent = {
  id: string
  code: string
  name: string
  status: string
}

export function getMyEvents(accessToken: string): Promise<OrganizerEvent[]> {
  return apiFetch<OrganizerEvent[]>('/api/v1/events', {
    accessToken,
  })
}