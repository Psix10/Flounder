const words = {
  'заход': 'заплыв', 'захода': 'заплыва', 'заходу': 'заплыву',
  'заходом': 'заплывом', 'заходе': 'заплыве', 'заходы': 'заплывы',
  'заходов': 'заплывов', 'заходам': 'заплывам', 'заходами': 'заплывами',
  'заходах': 'заплывах',
}
const legacyWord = /(^|[^\p{L}\p{N}_])(заходами|заходах|заходам|заходов|заходом|захода|заходу|заходе|заходы|заход)(?=$|[^\p{L}\p{N}_])/giu
export function formatHeatLabel(value) {
  if (value == null) return '—'
  return value.replace(legacyWord, (_match, prefix, word) => {
    const translated = words[word.toLocaleLowerCase('ru-RU')]
    const result = word === word.toLocaleUpperCase('ru-RU')
      ? translated.toLocaleUpperCase('ru-RU')
      : word[0] === word[0].toLocaleUpperCase('ru-RU')
        ? translated[0].toLocaleUpperCase('ru-RU') + translated.slice(1)
        : translated
    return prefix + result
  })
}
