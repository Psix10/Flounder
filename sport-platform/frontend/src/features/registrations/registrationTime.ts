export function formatRegistrationSeedTime(value: unknown): string {
  if (value === undefined || value === null) {
    return 'Без заявочного времени (NT)'
  }

  if (
    typeof value !== 'number' ||
    !Number.isFinite(value) ||
    value <= 0
  ) {
    return 'Некорректное заявочное время'
  }

  const scaled = value * 100
  const totalHundredths = Math.round(scaled)

  if (
    !Number.isSafeInteger(totalHundredths) ||
    totalHundredths <= 0 ||
    Math.abs(scaled - totalHundredths) > 0.000001
  ) {
    return 'Некорректное заявочное время'
  }

  const minutes = Math.floor(totalHundredths / 6000)
  const seconds = Math.floor((totalHundredths % 6000) / 100)
  const hundredths = totalHundredths % 100

  return (
    `${String(minutes).padStart(2, '0')}:` +
    `${String(seconds).padStart(2, '0')}.` +
    String(hundredths).padStart(2, '0')
  )
}