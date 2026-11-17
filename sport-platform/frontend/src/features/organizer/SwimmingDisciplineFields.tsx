import type { SwimmingFields } from '../../lib/swimming-discipline-settings.mjs'
import { SWIMMING_STROKES } from '../../lib/swimming-discipline-settings.mjs'
import styles from './CreateEventDisciplineForm.module.css'

export function SwimmingDisciplineFields({ value, onChange }: { value: SwimmingFields; onChange: (value: SwimmingFields) => void }) {
  function change(key: keyof SwimmingFields, next: string | boolean) { onChange({ ...value, [key]: next }) }
  return <section>
    <label className={styles.field}><span><input type="checkbox" checked={value.enabled} onChange={event => change('enabled', event.target.checked)} /> Настроить параметры плавания для этой дисциплины</span></label>
    {!value.enabled ? <p className={styles.hint}>Без переопределений сохраняются настройки выбранного шаблона. Название и код не определяют дистанцию, стиль или пол.</p> : <>
      <div className={styles.grid}>
        <label className={styles.field}>Дистанция, м<input required type="number" min="1" max="2147483647" step="1" value={value.distance} onChange={event => change('distance', event.target.value)} /></label>
        <label className={styles.field}>Стиль<select required value={value.stroke} onChange={event => change('stroke', event.target.value)}><option value="">Выберите стиль</option>{SWIMMING_STROKES.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
        <label className={styles.field}>Пол<select required value={value.gender} onChange={event => change('gender', event.target.value)}><option value="">Выберите вариант</option><option value="MALE">Юноши / мужчины</option><option value="FEMALE">Девушки / женщины</option><option value="OPEN">Совместная дисциплина, без ограничения по полу</option></select></label>
        <label className={styles.field}>Длина бассейна, м<input type="number" min="1" max="2147483647" step="1" placeholder="Унаследовать из шаблона" value={value.pool} onChange={event => change('pool', event.target.value)} /></label>
      </div>
      <label className={styles.field}>Разрешённые коды категорий регламента<input value={value.categoryCodes} onChange={event => change('categoryCodes', event.target.value)} placeholder="Например: M_2014_2016, M_2012_2013, M_2010_2011" /></label>
      <p className={styles.hint}>Необязательное расширенное поле. Коды должны существовать в привязанном регламенте. Пустое поле не переопределяет фильтр шаблона; если и в шаблоне фильтра нет, применяются все категории регламента с отдельной проверкой пола дисциплины. Не вводите годы рождения вместо кодов. Сервер проверит коды перед генерацией. Выбор групп по названиям — следующий UX-этап.</p>
    </>}
  </section>
}
