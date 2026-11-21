const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function parseDisciplineId(value) {
  const disciplineId = typeof value === 'string' ? value.trim() : ''
  if (!disciplineId) {
    return { disciplineId: '', errorMessage: 'Введите ID дисциплины.' }
  }
  if (!UUID.test(disciplineId)) {
    return {
      disciplineId: '',
      errorMessage: 'Некорректный ID дисциплины. Ожидается UUID вида xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.',
    }
  }
  return { disciplineId, errorMessage: null }
}
