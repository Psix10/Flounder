import { formatHeatLabel } from './heat-labels.mjs'

function text(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

export function registrationEventTitle(registration) {
  return text(registration?.eventTitle) ?? 'Название мероприятия недоступно'
}

export function registrationDisciplineName(registration) {
  const name = text(registration?.disciplineName)
  return name ? formatHeatLabel(name) : 'Название дисциплины недоступно'
}

export function registrationParticipantName(registration) {
  const person = registration?.participantSnapshot
  return [person?.lastName, person?.firstName, person?.middleName]
    .map(text).filter(Boolean).join(' ') || 'ФИО не указано'
}

export function mergeRegistrationDisplay(current, updated) {
  if (current.id !== updated.id ||
      current.eventId !== updated.eventId ||
      current.eventDisciplineId !== updated.eventDisciplineId) {
    return { ...updated }
  }
  return {
    ...updated,
    eventTitle: updated.eventTitle ?? current.eventTitle,
    disciplineName: updated.disciplineName ?? current.disciplineName,
  }
}
