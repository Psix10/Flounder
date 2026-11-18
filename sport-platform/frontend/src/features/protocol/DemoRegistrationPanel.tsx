// SWIM_LABELS_V1
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/http'
import { createDemoRegistrations, getDemoPlan, type DemoPlan } from '../../api/demo-data.api'
import { buildDemoRequest, isMatchingDemoPlan } from './demo-data-model.mjs'
import styles from './DemoRegistrationPanel.module.css'

type Props = {
  eventId: string
  disciplineId: string
  token: string
  disabled: boolean
  onAcquire: () => boolean
  onRelease: () => void
  onCreated: () => void
}
export function DemoRegistrationPanel({
  eventId, disciplineId, token, disabled, onAcquire, onRelease, onCreated,
}: Props) {
  const [plan, setPlan] = useState<DemoPlan | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [hidden, setHidden] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [mustRefresh, setMustRefresh] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const submitting = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    setConfirmed(false)
    async function load() {
      try {
        const value = await getDemoPlan(eventId, disciplineId, token)
        if (!isMatchingDemoPlan(value, eventId, disciplineId)) throw new Error('План относится к другой дисциплине.')
        if (active) { setPlan(value); setMustRefresh(false); setHidden(false) }
      } catch (failure) {
        if (!active) return
        setPlan(null)
        if (failure instanceof ApiError && failure.status === 404 && failure.code === 'demo.unavailable') {
          setHidden(true)
        } else {
          setError(failure instanceof ApiError || failure instanceof Error ? failure.message : 'Не удалось загрузить демоплан.')
        }
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [eventId, disciplineId, token, reload])

  async function create() {
    if (!plan || disabled || loading || mustRefresh || submitting.current || !confirmed) return
    if (!isMatchingDemoPlan(plan, eventId, disciplineId)) return
    let request
    try { request = buildDemoRequest(plan, crypto.randomUUID(), confirmed) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Не удалось подготовить запрос.'); return }
    if (!window.confirm(
      `Создать ${plan.count} подтверждённых тестовых заявок?\n\n` +
      `Дисциплина: ${disciplineId}\nВместимость: ${plan.capacity}\nВерсия правил: ${plan.ruleVersionId}\n\n` +
      'Это новые неактивные демоаккаунты, без платежей и уведомлений. ' +
      'Протокол сейчас не создаётся. Быстрой очистки набора в этой версии нет.'
    )) return
    if (!onAcquire()) return
    submitting.current = true
    setSaving(true)
    setError(null)
    let committed = false
    try {
      await createDemoRegistrations(eventId, disciplineId, request, token)
      committed = true
      if (mounted.current) { setConfirmed(false); setMustRefresh(true); onCreated() }
    } catch (failure) {
      if (mounted.current) {
        setMustRefresh(true)
        setConfirmed(false)
        const detail = failure instanceof ApiError || failure instanceof Error ? failure.message : 'Ответ не получен.'
        setError((committed ? 'Данные созданы, но экран не обновился.' : 'Результат запроса требует проверки.') +
          '\n' + detail + '\nОбновите демоплан перед следующей попыткой. Автоматический повтор отключён.')
      }
    } finally {
      submitting.current = false
      if (mounted.current) setSaving(false)
      onRelease()
    }
  }
  if (hidden) return null
  return <section className={styles.panel} aria-busy={saving || loading}>
    <h2>Демонстрационные заявки</h2>
    <p>Только для разрешённого демособытия. Это подготовка фикстур, а не прохождение регистрации участником.</p>
    {loading ? <p role="status">Проверяем возможность наполнения…</p> : null}
    {error ? <p role="alert" className={styles.error}>{error}</p> : null}
    {plan ? <>
      <p role="status">{plan.message}</p>
      <p>Вместимость: {plan.capacity}. Размер набора: {plan.count}. Поле времени: seedTimeSeconds.</p>
      {plan.receipt ? <p>Создан набор {plan.receipt.requestId}. Повторное наполнение недоступно.</p> : null}
      <p>Количество заплывов зависит от правил и групп посева. Наполнение само не сохраняет протокол.</p>
      <div className={styles.table}><table>
        <thead><tr><th>Участник</th><th>Пол в снимке</th><th>Дата рождения</th><th>Категория</th><th>Группа посева</th><th>Заявочное время</th></tr></thead>
        <tbody>{plan.participants.map((row) => <tr key={row.ordinal}>
          <td>{row.name}</td><td>{row.gender === 'MALE' ? 'Мужской' : row.gender === 'FEMALE' ? 'Женский' : '—'}</td>
          <td>{row.birthDate ?? '—'}</td><td>{row.categoryCode ?? 'Без категорий'}</td><td>{row.seedingGroupCode ?? '—'}</td>
          <td>{row.seedTimeSeconds == null ? 'Без времени (NT)' : `${row.seedTimeSeconds.toFixed(2)} с`}</td>
        </tr>)}</tbody>
      </table></div>
      {plan.state === 'AVAILABLE' ? <label className={styles.confirm}>
        <input type="checkbox" checked={confirmed} disabled={disabled || loading || saving || mustRefresh}
          onChange={(event) => setConfirmed(event.target.checked)} />
        <span>Подтверждаю создание {plan.count} тестовых заявок в этой пустой демодисциплине.
          Участники не смогут входить в систему; платежи не создаются. Регламент и реальные заявки не изменяются.</span>
      </label> : null}
    </> : null}
    <div className={styles.actions}>
      <button type="button" className={styles.button} disabled={disabled || loading || saving}
        onClick={() => setReload((value) => value + 1)}>Обновить демоплан</button>
      {plan?.state === 'AVAILABLE' ? <button type="button" className={styles.button}
        disabled={disabled || loading || saving || mustRefresh || !confirmed} onClick={() => void create()}>
        {saving ? 'Создаём тестовый набор…' : `Добавить ${plan.count} тестовых заявок`}
      </button> : null}
    </div>
  </section>
}
