// SWIM_LABELS_V1
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { NavLink, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../../api/http'
import { getRegulationConfiguration, publishRegulationConfiguration, type ProtocolSettings, type ProtocolCategory } from '../../api/regulations.api'
import { getSports, type SportResponse } from '../../api/sports.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { parseCategoryJson, presetCategories } from './category-settings.mjs'
import type { CategoryMode } from './category-settings.mjs'
import styles from './CreateRegulationPage.module.css'

export function CreateRegulationPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const sourceId = params.get('sourceVersionId')
  const { session } = useAuth()
  const token = session?.accessToken
  const [sports, setSports] = useState<SportResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [sourceLoaded, setSourceLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sportId, setSportId] = useState('')
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [version, setVersion] = useState('1')
  const [date, setDate] = useState('')
  const [notes, setNotes] = useState('')
  const [text, setText] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [capacity, setCapacity] = useState('8')
  const [positions, setPositions] = useState('4,5,3,6,2,7,1,8')
  const [metricKey, setMetricKey] = useState('seedTimeSeconds')
  const [heatOrder, setHeatOrder] = useState<ProtocolSettings['heatOrder']>('SLOWEST_FIRST')
  const [partialHeat, setPartialHeat] = useState<ProtocolSettings['partialHeat']>('FIRST')
  const [categoryMode, setCategoryMode] = useState<CategoryMode>('NONE')
  const [categoryJson, setCategoryJson] = useState('[]')
  const saving = useRef(false)

  function changeCategoryMode(next: CategoryMode) {
    if (next === 'CUSTOM' && categoryMode !== 'CUSTOM') {
      setCategoryJson(JSON.stringify(presetCategories(categoryMode), null, 2))
    }
    setCategoryMode(next)
  }

  useEffect(() => {
    let active = true
    async function load() {
      setLoading(true); setError(null); setSourceLoaded(false)
      setCategoryMode('NONE'); setCategoryJson('[]')
      if (!token) { setError('Войдите в аккаунт организатора.'); setLoading(false); return }
      try {
        const [items, existing] = await Promise.all([
          getSports(token), sourceId ? getRegulationConfiguration(sourceId, token) : Promise.resolve(null),
        ])
        if (!active) return
        setSports(items.filter((item) => item.isActive))
        if (existing) {
          const d = existing.document
          setSportId(d.sportId); setCode(d.code); setName(d.name); setDescription(d.description ?? '')
          setVersion(String(d.versionNo + 1)); setDate(d.effectiveFrom ?? ''); setNotes('')
          setText(d.conditions.join('\n')); setEnabled(existing.protocol !== null)
          if (existing.protocol) {
            const p = existing.protocol
            setCapacity(String(p.capacity)); setPositions(p.positionOrder.join(',')); setMetricKey(p.metricKey)
            setHeatOrder(p.heatOrder); setPartialHeat(p.partialHeat)
            if (p.categories?.length) {
              setCategoryMode('CUSTOM')
              setCategoryJson(JSON.stringify(p.categories, null, 2))
            }
          }
        }
        setSourceLoaded(true)
      } catch (failure) {
        if (active) setError(failure instanceof ApiError ? failure.message : 'Не удалось загрузить данные.')
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [token, sourceId])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || saving.current || loading || !sourceLoaded) return
    setError(null)
    const no = Number(version)
    if (!sportId || !code.trim() || !name.trim() || !Number.isSafeInteger(no) || no < 1 || (!sourceId && no !== 1)) {
      setError('Заполните основные сведения. Новый регламент начинается с версии 1.'); return
    }
    let protocol: ProtocolSettings | null = null
    if (enabled) {
      const count = Number(capacity), entries = positions.split(',').map((value) => value.trim())
      const order = entries.map(Number)
      if (!Number.isInteger(count) || count < 1 || count > 1024 || entries.some((v) => !v) ||
          order.length !== count || order.some((v) => !Number.isInteger(v) || v < 0 || v > 2147483647) ||
          new Set(order).size !== count || !metricKey.trim()) {
        setError('Проверьте число дорожек, уникальные номера дорожек и поле заявочного результата.'); return
      }
      let categories: ProtocolCategory[]
      try {
        categories = categoryMode === 'CUSTOM'
          ? parseCategoryJson(categoryJson)
          : presetCategories(categoryMode)
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Проверьте категории.')
        return
      }
      protocol = {
        capacity: count, positionOrder: order, metricKey: metricKey.trim(), heatOrder, partialHeat,
        ...(categories.length ? { categories } : {}),
      }
    }
    saving.current = true; setBusy(true)
    try {
      const result = await publishRegulationConfiguration({
        sourceVersionId: sourceId, sportId, code: code.trim(), name: name.trim(),
        description: description.trim() || null, versionNo: no, effectiveFrom: date || null,
        notes: notes.trim() || null, conditions: text.split('\n').map((line) => line.trim()).filter(Boolean), protocol,
      }, token)
      navigate('/organizer/events/new', { replace: true, state: { createdRegulationVersionId: result.document.id } })
    } catch (failure) {
      setError(failure instanceof ApiError
        ? [failure.message, ...failure.violations.map((v) => `${v.field}: ${v.message}`)].join('\n')
        : 'Не удалось сохранить регламент. Проверьте соединение. После сетевой ошибки проверьте список версий перед повтором.')
    } finally { saving.current = false; setBusy(false) }
  }

  return <section className={styles.page}>
    <NavLink className={styles.backLink} to="/organizer/events/new">← К созданию мероприятия</NavLink>
    <h1 className={styles.title}>{sourceId ? 'Новая версия регламента' : 'Новый регламент'}</h1>
    <p className={styles.description}>Текстовые условия и настройки формирования сохраняются одной операцией. Опубликованные версии не изменяются.</p>
    {loading ? <p role="status">Загружаем данные…</p> : null}
    <form className={styles.form} onSubmit={submit}>
      <fieldset className={styles.editorFields} disabled={busy || loading || !sourceLoaded}>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Основные сведения</h2>
          {sourceId ? <p>Для новой версии вид спорта и общие сведения остаются прежними. Меняются условия и настройки.</p> : null}
          <div className={styles.grid}>
            <label className={styles.field}>Вид спорта<select className={styles.input} required disabled={!!sourceId} value={sportId} onChange={(e) => setSportId(e.target.value)}><option value="">Выберите вид спорта</option>{sports.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
            <label className={styles.field}>Код<input className={styles.input} required disabled={!!sourceId} maxLength={64} value={code} onChange={(e) => setCode(e.target.value)} /></label>
            <label className={styles.field}>Название<input className={styles.input} required disabled={!!sourceId} maxLength={255} value={name} onChange={(e) => setName(e.target.value)} /></label>
            <label className={styles.field}>Номер версии<input className={styles.input} required type="number" min="1" max="2147483647" disabled={!sourceId} value={version} onChange={(e) => setVersion(e.target.value)} /></label>
            <label className={`${styles.field} ${styles.fieldFull}`}>Описание<textarea className={styles.textarea} disabled={!!sourceId} maxLength={10000} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
            <label className={styles.field}>Действует с<input className={styles.input} type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
          </div>
        </article>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Текстовые условия</h2>
          <p>Каждое условие с новой строки. Текст не превращается автоматически в алгоритмическую проверку возраста, справки или допуска.</p>
          <textarea className={styles.textarea} rows={8} value={text} onChange={(e) => setText(e.target.value)} />
          <label className={styles.field}>Примечание к версии<textarea className={styles.textarea} maxLength={10000} value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        </article>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Формирование стартового протокола</h2>
          <label><input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} /> Настроить индивидуальные заплывы</label>
          {enabled ? <>
            <p>Общие настройки для совместимых дисциплин этого регламента. Категории определяют отдельный зачёт; группы посева определяют, кто может попасть в один заплыв. Расписание и индивидуальные исключения здесь не настраиваются.</p>
            <div className={styles.grid}>
              <label className={styles.field}>Вместимость заплыва<input className={styles.input} required type="number" min="1" max="1024" value={capacity} onChange={(e) => setCapacity(e.target.value)} /></label>
              <label className={styles.field}>Порядок дорожек<input className={styles.input} required value={positions} onChange={(e) => setPositions(e.target.value)} /></label>
              <label className={styles.field}>Порядок заплывов<select className={styles.input} value={heatOrder} onChange={(e) => setHeatOrder(e.target.value as ProtocolSettings['heatOrder'])}><option value="SLOWEST_FIRST">От слабых к сильным</option><option value="FASTEST_FIRST">От сильных к слабым</option></select></label>
              <label className={styles.field}>Неполный заплыв<select className={styles.input} value={partialHeat} onChange={(e) => setPartialHeat(e.target.value as ProtocolSettings['partialHeat'])}><option value="FIRST">В начале</option><option value="LAST">В конце</option></select></label>
              <label className={styles.field}>Поле заявочного результата<input className={styles.input} required maxLength={100} value={metricKey} onChange={(e) => setMetricKey(e.target.value)} /></label>
            </div>
            <section className={styles.card}>
              <h3 className={styles.cardTitle}>Категории зачёта и группы посева</h3>
              <label className={styles.field}>Режим категорий
                <select className={styles.input} value={categoryMode}
                  onChange={(e) => changeCategoryMode(e.target.value as CategoryMode)}>
                  <option value="NONE">Без категорий — прежний режим</option>
                  <option value="SEX_MIXED">Мужчины / женщины — совместные заплывы</option>
                  <option value="SEX_SEPARATE">Мужчины / женщины — раздельные заплывы</option>
                  <option value="CUSTOM">Пользовательские или сохранённые категории (JSON)</option>
                </select>
              </label>
              {categoryMode === 'NONE' ? <p>Категорийных мест не будет. При создании новой версии ранее настроенные категории в эту новую версию не переносятся.</p>
                : categoryMode === 'CUSTOM' ? <>
                  <p>Сохранённые категории загружены без преобразования в шаблон. Условия возраста и группы сохраняются. Каждый участник должен соответствовать ровно одной категории.</p>
                  <label className={styles.field}>Категории в формате JSON
                    <textarea className={styles.textarea} rows={14} value={categoryJson}
                      onChange={(e) => setCategoryJson(e.target.value)} spellCheck={false} />
                  </label>
                </> : <>
                  <p>Мужской и женский зачёт определяются по полу, сохранённому в заявке участника.</p>
                  <p>{categoryMode === 'SEX_MIXED'
                    ? 'Общая группа посева: участники мужского и женского зачётов могут попасть в один заплыв.'
                    : 'Раздельные группы посева: мужские и женские заплывы формируются отдельно.'}</p>
                  <p>Заявка без распознаваемого пола не будет молча исключена: сервер отклонит генерацию и вернёт ошибку классификации.</p>
                </>}
              {categoryMode !== 'NONE' ? <p>Текстовые условия сами по себе не задают категории. Итоговое соответствие участников категориям проверяет сервер при генерации.</p> : null}
            </section>
            <p>Участники без результата — в слабейшем заплыве. Сбор заявочного времени этой формой не добавляется; отсутствующее время не заменяется нулём.</p>
          </> : <p>Регламент будет опубликован без настроек генерации. Чтобы добавить их позже, создайте новую версию.</p>}
        </article>
        <div className={styles.actions}><button className={styles.primaryButton} type="submit" disabled={busy || loading}>{busy ? 'Сохраняем…' : 'Сохранить и опубликовать регламент'}</button></div>
      </fieldset>
      {error ? <p className={styles.errorMessage} role="alert">{error}</p> : null}
    </form>
  </section>
}
