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
  title: string
  publicSlug: string
  status: string
}

export function getMyEvents(accessToken: string): Promise<OrganizerEvent[]> {
  return apiFetch<OrganizerEvent[]>('/api/v1/events', {
    accessToken,
  })
}



export type EventResponse = {
  id: string
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
  title: string
  description: string | null
  registrationOpenAt: string | null
  registrationCloseAt: string | null
  eventStartAt: string
  eventEndAt: string
  status: string
  publicSlug: string | null
  settingsJson: string | null
  createdAt: string
  updatedAt: string
  disciplines: Array<{
    id: string
    code: string
    name: string
    competitionFormat: string
    unitType: string
    resultType: string
    rankingStrategy: string
    participantLimit: number | null
    entryFeeAmount: number
    entryFeeCurrency: string
    settingsJson: string | null
  }>
}

export function getEventById(
  eventId: string,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(
    `/api/v1/events/${encodeURIComponent(eventId)}`,
    {
      accessToken,
    },
  )
}