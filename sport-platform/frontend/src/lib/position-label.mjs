export function positionLabel(value) {
  if (value == null) return 'Дорожка / позиция не назначена'
  if (typeof value !== 'number' ||
      !Number.isSafeInteger(value) || value < 0) {
    return 'Некорректный номер дорожки / позиции'
  }
  return `Дорожка / позиция ${value}`
}
