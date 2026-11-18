import type { EventDiscipline } from '../../api/events.api'

export function buildSessionDisciplineLabels(
  disciplines: readonly Pick<EventDiscipline, 'id' | 'name' | 'code'>[],
): Map<string, string> {
  const counts = new Map<string, number>()
  for (const { name } of disciplines) {
    counts.set(name, (counts.get(name) ?? 0) + 1)
  }

  const labels = new Map<string, string>()
  disciplines.forEach(({ id, name, code }, index) => {
    const suffix = code?.trim() || `дисциплина № ${index + 1}`
    labels.set(id, (counts.get(name) ?? 0) > 1 ? `${name} · ${suffix}` : name)
  })
  return labels
}
