import { inspectCategories } from './protocol-inspection.mjs'

const unavailable = message => ({
  state: 'unavailable',
  message,
  categories: [],
})

export async function readEventCategories(versionId, readConfiguration, formatError) {
  if (!versionId) {
    return unavailable('В данных мероприятия не указана привязанная версия регламента. Описания категорий недоступны.')
  }

  let configuration
  try {
    configuration = await readConfiguration(versionId)
  } catch (error) {
    return unavailable(formatError(
      error,
      'Не удалось загрузить категории регламента. Обновите данные мероприятия.',
    ))
  }

  if (configuration?.document?.id !== versionId) {
    return unavailable('Получена конфигурация другой версии регламента. Описания категорий недоступны.')
  }

  return inspectCategories(configuration.protocol)
}
