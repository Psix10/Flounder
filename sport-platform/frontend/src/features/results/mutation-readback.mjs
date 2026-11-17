// SWIM_LABELS_V1
export function assertUnitSnapshot(value, unitId, expectedStatus = null) {
  if (!value || typeof value !== 'object' || value.id !== unitId || !Array.isArray(value.entries)) {
    throw new Error('API чтения не вернул корректное состояние выбранного заплыва. Повторите только сверку данных, не изменяющий запрос.')
  }
  if (expectedStatus != null && value.status !== expectedStatus) {
    throw new Error(`Сервер вернул статус «${String(value.status)}» вместо ожидаемого «${expectedStatus}». Сверьте состояние перед следующей операцией.`)
  }
  return value
}
export async function mutateAndReadUnit(mutate, read, unitId, expectedStatus = null) {
  await mutate()
  let snapshot
  try { snapshot = await read() }
  catch (cause) {
    const error = new Error('Изменяющий запрос завершился без ошибки, но актуальные данные не прочитаны. Не повторяйте запись автоматически. ' +
      (cause instanceof Error ? cause.message : 'Проверьте соединение.'))
    error.cause = cause
    throw error
  }
  return assertUnitSnapshot(snapshot, unitId, expectedStatus)
}
