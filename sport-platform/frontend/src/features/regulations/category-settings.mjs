export function presetCategories(mode) {
  if (mode === 'NONE') return []
  if (mode !== 'SEX_MIXED' && mode !== 'SEX_SEPARATE') {
    throw new Error('Неизвестный режим категорий.')
  }
  return [
    { code: 'MEN', seedingGroupCode: mode === 'SEX_MIXED' ? 'OPEN' : 'MEN',
      eligibility: { allowedGenders: ['MALE'] } },
    { code: 'WOMEN', seedingGroupCode: mode === 'SEX_MIXED' ? 'OPEN' : 'WOMEN',
      eligibility: { allowedGenders: ['FEMALE'] } },
  ]
}

export function parseCategoryJson(raw) {
  let categories
  try { categories = JSON.parse(raw) }
  catch { throw new Error('Категории: некорректный JSON.') }
  if (!Array.isArray(categories) || categories.length < 1 || categories.length > 256) {
    throw new Error('Укажите от 1 до 256 категорий или выберите «Без категорий».')
  }
  const codes = new Set()
  const identifier = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$/
  const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
  const allowedFields = new Set(['code', 'seedingGroupCode', 'eligibility'])
  const eligibilityFields = new Set(['ageMethod', 'ageReferenceDate', 'minAge', 'maxAge', 'allowedGenders'])
  for (const [index, category] of categories.entries()) {
    const prefix = `Категория ${index + 1}: `
    if (!object(category) || Object.keys(category).some(key => !allowedFields.has(key))) {
      throw new Error(prefix + 'допустимы только code, seedingGroupCode и eligibility.')
    }
    if (typeof category.code !== 'string' || !identifier.test(category.code) ||
        typeof category.seedingGroupCode !== 'string' || !identifier.test(category.seedingGroupCode)) {
      throw new Error(prefix + 'коды должны содержать 1–80 латинских букв, цифр, _, . или -; первый символ — буква или цифра.')
    }
    if (codes.has(category.code)) throw new Error(prefix + 'повторяющийся код ' + category.code)
    codes.add(category.code)
    const eligibility = category.eligibility
    if (!object(eligibility) || Object.keys(eligibility).some(key => !eligibilityFields.has(key))) {
      throw new Error(prefix + 'некорректные условия eligibility.')
    }
    if ('allowedGenders' in eligibility) {
      const genders = eligibility.allowedGenders
      if (!Array.isArray(genders) || !genders.length || new Set(genders).size !== genders.length ||
          genders.some(value => value !== 'MALE' && value !== 'FEMALE')) {
        throw new Error(prefix + 'allowedGenders должен содержать уникальные MALE/FEMALE.')
      }
    }
    const ageKeys = ['ageMethod', 'ageReferenceDate', 'minAge', 'maxAge']
    if (ageKeys.some(key => key in eligibility)) {
      if (!['ON_DATE', 'BY_YEAR'].includes(eligibility.ageMethod) ||
          typeof eligibility.ageReferenceDate !== 'string' ||
          !/^\d{4}-\d{2}-\d{2}$/.test(eligibility.ageReferenceDate) ||
          !('minAge' in eligibility || 'maxAge' in eligibility)) {
        throw new Error(prefix + 'для возраста нужны ageMethod, ageReferenceDate и хотя бы одна граница.')
      }
      for (const key of ['minAge', 'maxAge']) {
        if (key in eligibility && (!Number.isInteger(eligibility[key]) || eligibility[key] < 0 || eligibility[key] > 2147483647)) {
          throw new Error(prefix + key + ' должен быть неотрицательным целым числом.')
        }
      }
      if ('minAge' in eligibility && 'maxAge' in eligibility && eligibility.minAge > eligibility.maxAge) {
        throw new Error(prefix + 'minAge не должен превышать maxAge.')
      }
    }
  }
  return categories
}
