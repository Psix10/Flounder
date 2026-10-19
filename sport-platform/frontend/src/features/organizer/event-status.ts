export type EventStatusTone =
  | 'draft'
  | 'published'
  | 'open'
  | 'closed'
  | 'live'
  | 'completed'
  | 'archived'
  | 'neutral'

export function normalizeEventStatus(status?: string | null): string {
  if (!status) {
    return 'UNKNOWN'
  }

  switch (status.trim().toUpperCase()) {
    case 'REGISTRATIONOPEN':
      return 'REGISTRATION_OPEN'
    case 'REGISTRATIONCLOSED':
      return 'REGISTRATION_CLOSED'
    default:
      return status.trim().toUpperCase()
  }
}

export function getEventStatusLabel(status?: string | null): string {
  switch (normalizeEventStatus(status)) {
    case 'DRAFT':
      return 'Черновик'
    case 'PUBLISHED':
      return 'Опубликовано'
    case 'REGISTRATION_OPEN':
      return 'Регистрация открыта'
    case 'REGISTRATION_CLOSED':
      return 'Регистрация закрыта'
    case 'LIVE':
      return 'В процессе'
    case 'COMPLETED':
      return 'Завершено'
    case 'ARCHIVED':
      return 'В архиве'
    default:
      return status || 'Неизвестно'
  }
}

export function getEventStatusTone(status?: string | null): EventStatusTone {
  switch (normalizeEventStatus(status)) {
    case 'DRAFT':
      return 'draft'
    case 'PUBLISHED':
      return 'published'
    case 'REGISTRATION_OPEN':
      return 'open'
    case 'REGISTRATION_CLOSED':
      return 'closed'
    case 'LIVE':
      return 'live'
    case 'COMPLETED':
      return 'completed'
    case 'ARCHIVED':
      return 'archived'
    default:
      return 'neutral'
  }
}