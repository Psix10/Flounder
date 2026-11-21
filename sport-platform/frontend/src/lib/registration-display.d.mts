type DisplayRegistration = {
  eventTitle?: string | null
  disciplineName?: string | null
  participantSnapshot?: {
    firstName?: string | null
    lastName?: string | null
    middleName?: string | null
  } | null
}
export function registrationEventTitle(value: DisplayRegistration | null | undefined): string
export function registrationDisciplineName(value: DisplayRegistration | null | undefined): string
export function registrationParticipantName(value: DisplayRegistration | null | undefined): string
export function mergeRegistrationDisplay<T extends {
  id: string
  eventId: string
  eventDisciplineId: string
  eventTitle?: string | null
  disciplineName?: string | null
}>(current: T, updated: T): T
