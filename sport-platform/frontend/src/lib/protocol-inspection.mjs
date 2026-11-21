const ID = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$/
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const genders = { MALE: 'Мужской', FEMALE: 'Женский' }
const strokes = { FREESTYLE: 'Вольный стиль', FREE: 'Вольный стиль', BREASTSTROKE: 'Брасс', BREAST: 'Брасс', BUTTERFLY: 'Баттерфляй', FLY: 'Баттерфляй', BACKSTROKE: 'На спине', BACK: 'На спине', MEDLEY: 'Комплексное плавание' }
export function parseCalendarDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [year, month, day] = value.split('-').map(Number)
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (month < 1 || month > 12 || day < 1 || day > days[month - 1]) return null
  return { year, month, day, iso: value }
}
export function snapshotYear(snapshot) {
  const parsed = parseCalendarDate(snapshot?.birthDate)
  return parsed ? String(parsed.year).padStart(4, '0') : snapshot?.birthDate == null ? 'Не указан' : 'Дата некорректна / формат не поддержан'
}
export function snapshotGender(snapshot) {
  const value = snapshot?.gender
  if (value == null) return 'Не указан'
  return typeof value === 'string' ? genders[value.toUpperCase()] ?? `Неизвестное значение: ${value}` : 'Некорректное значение'
}
function policy(raw) {
  if (!isObject(raw)) throw new Error('У категории отсутствуют настройки допуска.')
  if (Object.keys(raw).some(key => !['ageMethod','ageReferenceDate','minAge','maxAge','allowedGenders'].includes(key))) throw new Error('Неизвестные параметры допуска: предварительная проверка недоступна.')
  let allowed = []
  if ('allowedGenders' in raw) {
    allowed = raw.allowedGenders
    if (!Array.isArray(allowed) || !allowed.length || new Set(allowed).size !== allowed.length || allowed.some(item => !['MALE','FEMALE'].includes(item))) throw new Error('Некорректный список полов категории.')
  }
  const age = ['ageMethod','ageReferenceDate','minAge','maxAge'].some(key => key in raw)
  if (!age) return { age: false, genders: allowed }
  const reference = parseCalendarDate(raw.ageReferenceDate)
  if (!['BY_YEAR','ON_DATE'].includes(raw.ageMethod) || !reference || !('minAge' in raw || 'maxAge' in raw)) throw new Error('Неполное или неподдерживаемое правило возраста.')
  for (const key of ['minAge','maxAge']) {
    if (key in raw && (!Number.isInteger(raw[key]) || raw[key] < 0 || raw[key] > 2147483647)) throw new Error('Некорректная граница возраста.')
  }
  if ('minAge' in raw && 'maxAge' in raw && raw.minAge > raw.maxAge) throw new Error('Перепутаны границы возраста.')
  return { age: true, genders: allowed, method: raw.ageMethod, reference, min: raw.minAge, max: raw.maxAge }
}
function describe(p) {
  const sex = p.genders.length ? p.genders.map(item => genders[item]).join(' / ') : 'Любой пол'
  if (!p.age) return `${sex} · Без возрастного ограничения`
  const year = p.reference.year
  if (p.method === 'BY_YEAR') {
    const birth = p.min != null && p.max != null
      ? `${year - p.max}–${year - p.min} г. р.`
      : p.min != null ? `${year - p.min} г. р. и старше` : `${year - p.max} г. р. и младше`
    return `${sex} · ${birth} · возраст по году рождения, опорная дата ${p.reference.iso}`
  }
  const age = p.min != null && p.max != null ? `${p.min}–${p.max}` : p.min != null ? `от ${p.min}` : `до ${p.max}`
  return `${sex} · ${age} полных лет на ${p.reference.iso}`
}
export function inspectCategories(settings) {
  if (settings == null) return { state: 'unavailable', message: 'Конфигурация категорий не загружена.', categories: [] }
  if (!isObject(settings)) return { state: 'invalid', message: 'Неверный формат конфигурации.', categories: [] }
  if (settings.categories == null || Array.isArray(settings.categories) && settings.categories.length === 0) return { state: 'disabled', message: 'В регламенте категории не настроены. Возрастно-половое разделение этим правилом не задано.', categories: [] }
  try {
    if (!Array.isArray(settings.categories) || settings.categories.length > 256) throw new Error('Неверный список категорий.')
    const seen = new Set()
    const categories = settings.categories.map(raw => {
      if (!isObject(raw) || Object.keys(raw).some(key => !['code','seedingGroupCode','eligibility'].includes(key))) throw new Error('Неподдерживаемая структура категории.')
      if (typeof raw.code !== 'string' || !ID.test(raw.code) || seen.has(raw.code) || typeof raw.seedingGroupCode !== 'string' || !ID.test(raw.seedingGroupCode)) throw new Error('Неверный или повторяющийся код категории.')
      seen.add(raw.code)
      const parsed = policy(raw.eligibility)
      return { code: raw.code, seedingGroupCode: raw.seedingGroupCode, description: describe(parsed), policy: parsed }
    })
    return { state: 'ready', message: 'Категории из привязанной версии регламента мероприятия.', categories }
  } catch (error) {
    return { state: 'invalid', message: error instanceof Error ? error.message : 'Не удалось прочитать категории.', categories: [] }
  }
}
function violations(snapshot, p) {
  if (!p.age && !p.genders.length) return []
  if (!isObject(snapshot)) return ['Нет корректного снимка участника']
  const errors = []
  if (p.age) {
    const birth = parseCalendarDate(snapshot.birthDate)
    if (!birth) errors.push(snapshot.birthDate == null ? 'Не указана дата рождения' : 'Дата рождения некорректна / формат не поддержан')
    else if (birth.iso > p.reference.iso) errors.push('Дата рождения позже опорной даты')
    else {
      const beforeBirthday = p.reference.month < birth.month || p.reference.month === birth.month && p.reference.day < birth.day
      const age = p.reference.year - birth.year - (p.method === 'ON_DATE' && beforeBirthday ? 1 : 0)
      if (p.min != null && age < p.min || p.max != null && age > p.max) errors.push('Возраст вне границ категории')
    }
  }
  if (p.genders.length) {
    const gender = typeof snapshot.gender === 'string' ? snapshot.gender.toUpperCase() : null
    if (snapshot.gender == null) errors.push('Не указан пол')
    else if (!['MALE','FEMALE'].includes(gender)) errors.push('Некорректное значение пола')
    else if (!p.genders.includes(gender)) errors.push('Пол не соответствует категории')
  }
  return errors
}
export function previewCategory(snapshot, inspection) {
  if (inspection.state !== 'ready') return { state: inspection.state, text: inspection.message }
  const matched = [], diagnostics = new Set()
  for (const category of inspection.categories) {
    const errors = violations(snapshot, category.policy)
    if (!errors.length) matched.push(category)
    errors.forEach(error => diagnostics.add(error))
  }
  if (!matched.length) return { state: 'no-match', text: `Категория не найдена: ${[...diagnostics].join('; ')}` }
  if (matched.length > 1) return { state: 'ambiguous', text: `Пересечение категорий: ${matched.map(item => item.code).join(', ')}` }
  return { state: 'matched', text: matched[0].description, code: matched[0].code, seedingGroupCode: matched[0].seedingGroupCode }
}
export function disciplineFacts(raw) {
  let data
  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw }
  catch { return { error: 'Настройки дисциплины содержат некорректный JSON.', distance: 'Не задано', stroke: 'Не задано', gender: 'Не задано', pool: 'Не задано' } }
  if (data == null) data = {}
  if (!isObject(data)) return { error: 'Настройки дисциплины должны быть объектом.', distance: 'Не задано', stroke: 'Не задано', gender: 'Не задано', pool: 'Не задано' }
  const measure = value => value == null ? 'Не задано' : typeof value === 'number' && Number.isFinite(value) && value > 0 ? `${value} м` : 'Некорректное значение'
  const code = (value, labels) => value == null ? 'Не задано' : typeof value === 'string' ? labels[value.toUpperCase()] ?? `Код: ${value}` : 'Некорректное значение'
  return { error: null, distance: measure(data.distanceMeters), stroke: code(data.stroke, strokes), gender: code(data.gender, genders), pool: measure(data.poolLengthMeters) }
}
export function configuredGenderWarning(raw, snapshot) {
  let data
  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw } catch { return null }
  const restriction = typeof data?.gender === 'string' ? data.gender.toUpperCase() : null
  const actual = typeof snapshot?.gender === 'string' ? snapshot.gender.toUpperCase() : null
  if (['MALE','FEMALE'].includes(restriction) && ['MALE','FEMALE'].includes(actual) && restriction !== actual) return 'Пол, сохранённый в заявке, не соответствует ограничению дисциплины. Требуется проверка допуска на сервере.'
  return null
}
export function savedCategoryLabel(code, inspection) {
  if (code == null) return 'Категория не сохранена'
  const category = inspection.categories.find(item => item.code === code)
  return category ? category.description : 'Описание кода отсутствует в загруженной конфигурации'
}
