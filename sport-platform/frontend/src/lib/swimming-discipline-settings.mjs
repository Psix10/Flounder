const CODE=/^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$/
export const SWIMMING_STROKES=[['FREESTYLE','Вольный стиль'],['BREASTSTROKE','Брасс'],['BUTTERFLY','Баттерфляй'],['BACKSTROKE','На спине'],['MEDLEY','Комплексное плавание']]
export function emptySwimmingFields(){return {enabled:false,distance:'',stroke:'',gender:'',pool:'',categoryCodes:''}}
function integer(value,label){
  if(typeof value!=='string'||!/^\d+$/.test(value.trim()))throw new Error(`${label}: укажите целое положительное число.`)
  const number=Number(value)
  if(!Number.isSafeInteger(number)||number<=0||number>2147483647)throw new Error(`${label}: недопустимое значение.`)
  return number
}
export function buildSwimmingSettings(value){
  if(!value.enabled)return undefined
  const distanceMeters=integer(value.distance,'Дистанция')
  if(!SWIMMING_STROKES.some(([code])=>code===value.stroke))throw new Error('Выберите стиль плавания.')
  if(!['MALE','FEMALE','OPEN'].includes(value.gender))throw new Error('Выберите ограничение по полу либо совместную дисциплину.')
  const result={distanceMeters,stroke:value.stroke,gender:value.gender}
  if(value.pool.trim())result.poolLengthMeters=integer(value.pool,'Длина бассейна')
  const codes=value.categoryCodes.split(',').map(code=>code.trim()).filter(Boolean)
  if(codes.some(code=>!CODE.test(code))||new Set(codes).size!==codes.length||codes.length>256)throw new Error('Коды категорий должны быть уникальными идентификаторами через запятую.')
  if(codes.length)result.allowedCategoryCodes=codes
  return result
}

export function filterDisciplineCategoryInspection(inspection, rawSettings) {
  const invalid=message=>({state:'invalid',message,categories:[]})
  let data
  try { data=typeof rawSettings==='string'?JSON.parse(rawSettings):rawSettings }
  catch {return invalid('Фильтр категорий дисциплины содержит некорректные настройки.')}
  const codes=data?.allowedCategoryCodes
  if(codes==null)return inspection
  if(!Array.isArray(codes)||!codes.length||codes.some(code=>typeof code!=='string'||!CODE.test(code))||new Set(codes).size!==codes.length)return invalid('Неверный фильтр категорий дисциплины.')
  if(inspection.state!=='ready')return invalid('Дисциплина выбирает категории, но категории регламента недоступны или не настроены.')
  const known=new Set(inspection.categories.map(item=>item.code))
  if(codes.some(code=>!known.has(code)))return invalid('В дисциплине выбраны коды, отсутствующие в регламенте.')
  const selected=new Set(codes)
  return {...inspection,message:`Для этой дисциплины выбраны категории: ${codes.join(', ')}.`,categories:inspection.categories.filter(item=>selected.has(item.code))}
}
