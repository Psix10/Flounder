import { apiFetch } from './http'

export type EventStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'COMPLETED'
  | string

export type Event = {
  id: string
  organizationId?: string
  venueId?: string
  sportId: string
  regulationVersionId?: string
  title: string
  description: string | null
  publicSlug: string
  status: EventStatus
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  createdAt?: string
  updatedAt?: string
  disciplines?: Array<{
    id: string
    code?: string
    name: string
    competitionFormat: 'INDIVIDUAL' | 'TEAM' | string
    unitType?: string
    resultType?: string
    rankingStrategy?: string
    participantLimit: number | null
    entryFeeAmount: number
    entryFeeCurrency: string
    settingsJson: string | null
  }>
}

export type EventResponse = Event

export type CreateEventRequest = {
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
  title: string
  description?: string | null
  publicSlug?: string | null
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
}

export async function getPublicEvents(): Promise<Event[]> {
  return apiFetch<Event[]>('/api/v1/public/events')
}

export async function getPublicEventDetails(publicSlug: string): Promise<Event> {
  return apiFetch<Event>(
    `/api/v1/public/events/${encodeURIComponent(publicSlug)}`,
  )
}

export async function getMyEvents(accessToken: string): Promise<Event[]> {
  return apiFetch<Event[]>('/api/v1/organizer/events', {
    accessToken,
  })
}

export async function getEvent(id: string, accessToken?: string): Promise<Event> {
  return apiFetch<Event>(`/api/v1/events/${encodeURIComponent(id)}`, {
    accessToken,
  })
}

export async function getEventById(
  id: string,
  accessToken?: string,
): Promise<EventResponse> {
  return getEvent(id, accessToken)
}

export async function createEvent(
  request: CreateEventRequest,
  accessToken: string,
): Promise<Event> {
  return apiFetch<Event>('/api/v1/events', {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export async function publishEvent(id: string, accessToken: string): Promise<Event> {
  return apiFetch<Event>(`/api/v1/events/${encodeURIComponent(id)}/publish`, {
    method: 'POST',
    accessToken,
  })
}

export async function openEventRegistration(id: string, accessToken: string): Promise<Event> {
  return apiFetch<Event>(
    `/api/v1/events/${encodeURIComponent(id)}/open-registration`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export async function closeEventRegistration(id: string, accessToken: string): Promise<Event> {
  return apiFetch<Event>(
    `/api/v1/events/${encodeURIComponent(id)}/close-registration`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export async function completeEvent(id: string, accessToken: string): Promise<Event> {
  return apiFetch<Event>(`/api/v1/events/${encodeURIComponent(id)}/complete`, {
    method: 'POST',
    accessToken,
  })
}