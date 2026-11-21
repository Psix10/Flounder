export function resolveVenueTimeZone(value) {
  if (typeof value === 'string' && value.trim()) {
    const timeZone = value.trim()
    try {
      new Intl.DateTimeFormat('ru-RU', { timeZone }).format(0)
      return {
        timeZone,
        label: `Время площадки: ${timeZone}.`,
      }
    } catch {
      // Invalid stored timezone: use an explicitly labelled fallback.
    }
  }
  return {
    timeZone: 'UTC',
    label: 'Время показано в UTC: часовой пояс площадки не указан или некорректен.',
  }
}

export function venueTimeZoneLabel(value) {
  return resolveVenueTimeZone(value).label
}

export function formatVenueDate(value, venueTimezone) {
  if (value == null || value === '') return 'Не указано'
  if (typeof value !== 'string' ||
      !/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)) return 'Некорректная дата'

  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return 'Некорректная дата'

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: resolveVenueTimeZone(venueTimezone).timeZone,
  }).format(date)
}
