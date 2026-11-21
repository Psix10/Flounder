const formats = Object.freeze({
  INDIVIDUAL: 'индивидуальный',
  TEAM: 'командный',
})
const units = Object.freeze({
  HEAT: 'заплыв',
})
const results = Object.freeze({
  TIME: 'время',
  POINTS: 'очки',
})

function label(value, labels) {
  if (value == null || value === '') return '—'
  if (typeof value !== 'string') return 'Некорректное значение'
  return Object.hasOwn(labels, value)
    ? labels[value]
    : `Неизвестное значение: ${value}`
}

export function competitionFormatLabel(value) {
  return label(value, formats)
}
export function competitionUnitTypeLabel(value) {
  return label(value, units)
}
export function resultTypeLabel(value) {
  return label(value, results)
}
