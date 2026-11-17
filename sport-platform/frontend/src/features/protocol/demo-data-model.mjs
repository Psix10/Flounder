const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export function isMatchingDemoPlan(plan, eventId, disciplineId) {
  return Boolean(plan && plan.eventId === eventId && plan.disciplineId === disciplineId)
}
export function buildDemoRequest(plan, requestId, confirmed) {
  if (!plan || plan.state !== 'AVAILABLE') throw new Error('Сначала обновите доступный план наполнения.')
  if (!Number.isInteger(plan.capacity) || plan.capacity < 1 || plan.capacity > 32 || plan.count !== plan.capacity * 2) {
    throw new Error('Некорректный размер демонабора.')
  }
  if (!uuid.test(plan.ruleVersionId) || !uuid.test(requestId)) throw new Error('Не удалось подготовить идентификатор операции.')
  if (plan.datasetVersion !== 'SWIM_MIXED_V1') throw new Error('Версия демонабора изменилась. Обновите страницу.')
  if (confirmed !== true) throw new Error('Подтвердите создание тестовых данных.')
  return Object.freeze({
    requestId, expectedRuleVersionId: plan.ruleVersionId,
    expectedCapacity: plan.capacity, expectedCount: plan.count,
    datasetVersion: plan.datasetVersion, confirmed: true,
  })
}
