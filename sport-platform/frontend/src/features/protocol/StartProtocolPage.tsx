// PROTOCOL_INSPECTION_V1
import { ProtocolDisciplineFacts, ProtocolCategoryRules, ConfirmedRegistrationAudit, SavedParticipantFacts, SavedCategoryDetails } from './ProtocolInspection'
// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useParams } from 'react-router'
import { getEventById, getEventDisciplines, type Event, type EventDiscipline } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { getRegistrations } from '../../api/registrations.api'
import {
  generateStartProtocol, getDisciplineRegulationConfiguration,
  getStartProtocol, type DisciplineRegulationConfiguration, type StartProtocol,
} from '../../api/protocol.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from '../registrations/registration.types'
import styles from './StartProtocolPage.module.css'

function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Не удалось выполнить действие. Проверьте соединение и повторите попытку.'
  const messages: Record<string, string> = {
    'protocol.no_confirmed_registrations': 'Нет подтверждённых заявок для формирования протокола.',
    'protocol.already_generated': 'Протокол уже существует. Обновите страницу перед повторным формированием.',
    'protocol.rule_version_incompatible': 'Правила несовместимы с видом спорта, форматом или типом дисциплины.',
    'protocol.rule_version_not_published': 'Выберите опубликованную версию правил.',
    'protocol.regeneration_requires_snapshot': 'Для старого протокола нет снимка генерации. Автоматическое переформирование недоступно.',
  }
  if (error.status === 401) return 'Не удалось подтвердить вход. Войдите в систему заново.'
  if (error.status === 403) return 'Нет прав на это действие для выбранного мероприятия.'

  const message = messages[error.code ?? '']
  if (message) return message
  if (error.violations.length > 0) {
    return 'Не удалось пройти проверку. Проверьте заявки и настройки дисциплины.'
  }
  return 'Не удалось выполнить действие с протоколом. Обновите страницу и повторите попытку.'
}

function participantName(registration?: Registration): string {
  if (!registration) return 'Заявка не найдена в текущем списке'
  const p = registration.participantSnapshot
  return [p.lastName, p.firstName, p.middleName].filter(Boolean).join(' ')
}

export function StartProtocolPage() {
  const { eventId, disciplineId } = useParams()
  const { session } = useAuth()
  const token = session?.accessToken
  const [event, setEvent] = useState<Event | null>(null)
  const [discipline, setDiscipline] = useState<EventDiscipline | null>(null)
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [configuration, setConfiguration] = useState<DisciplineRegulationConfiguration | null>(null)
  const [protocol, setProtocol] = useState<StartProtocol | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const locked = useRef(false)

  useEffect(() => {
    let active = true
    async function load() {
      setLoading(true)
      setError(null)
      setMessage(null)
      setEvent(null)
      setDiscipline(null)
      setProtocol(null)
      setRegistrations([])
      setConfiguration(null)
      if (!eventId || !disciplineId || !token) {
        setError('Не указан маршрут или отсутствует сессия.')
        setLoading(false)
        return
      }
      try {
        const [eventData, disciplines] = await Promise.all([
          getEventById(eventId, token), getEventDisciplines(eventId, token),
        ])
        const selected = disciplines.find((item) => item.id === disciplineId)
        if (!selected) throw new Error('discipline-not-in-event')
        const [allRegistrations, linkedConfiguration, saved] = await Promise.all([
          getRegistrations(token, { eventId }),
          getDisciplineRegulationConfiguration(disciplineId, token),
          getStartProtocol(disciplineId, token).catch((failure: unknown) => {
            if (failure instanceof ApiError && failure.code === 'protocol.start_protocol_not_found') return null
            throw failure
          }),
        ])
        if (!active) return
        setEvent(eventData)
        setDiscipline(selected)
        setRegistrations(allRegistrations.filter((item) => item.eventDisciplineId === disciplineId))
        setConfiguration(linkedConfiguration)
        setProtocol(saved)
      } catch (failure) {
        if (active) setError(failure instanceof Error && failure.message === 'discipline-not-in-event'
          ? 'Дисциплина не принадлежит выбранному мероприятию.' : errorText(failure))
      } finally {
        if (active) setLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [eventId, disciplineId, token, reload])

  const compatible = discipline?.competitionFormat === 'INDIVIDUAL' && discipline?.unitType === 'HEAT'
  const published = discipline?.status?.toUpperCase() === 'PUBLISHED'
  const confirmed = registrations.filter((item) => item.status === 'CONFIRMED')
  const versionId = configuration?.ruleSetVersionId ?? ''

  async function handleGenerate() {
    if (!token || !disciplineId || !versionId || !compatible || !published || locked.current) return
    if (!window.confirm(protocol
      ? 'Переформировать протокол? Состав и ID черновых заплывов могут измениться. Сервер проверит допустимость операции.'
      : 'Сформировать и сохранить стартовый протокол по выбранным правилам?')) return
    locked.current = true
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const result = await generateStartProtocol(disciplineId, versionId, protocol !== null, token)
      setProtocol(result)
      setMessage(`Протокол сохранён: заплывов — ${result.unitCount}, назначений — ${result.assignmentCount}.`)
    } catch (failure) {
      setError(errorText(failure))
    } finally {
      locked.current = false
      setBusy(false)
    }
  }

  return <section className={styles.page}>
    <NavLink className={styles.link} to={eventId ? `/organizer/events/${encodeURIComponent(eventId)}` : '/organizer/events'}>← К мероприятию</NavLink>
    <h1>Стартовый протокол</h1>
    {loading ? <p role="status">Загружаем данные…</p> : null}
    {error ? <p className={styles.error} role="alert">{error}</p> : null}
    {message ? <p className={styles.success} role="status">{message}</p> : null}
    <button type="button" className={styles.secondary} disabled={busy || loading} onClick={() => setReload((value) => value + 1)}>Обновить данные</button>
    {!loading && event && discipline ? <>
      <header className={styles.card}>
        <h2>{formatHeatLabel(discipline.name)}</h2><p>{event.title}</p>
        <p>Формат: {discipline.competitionFormat === 'INDIVIDUAL' ? 'индивидуальный' : discipline.competitionFormat === 'TEAM' ? 'командный' : discipline.competitionFormat} · Тип старта: {discipline.unitType === 'HEAT' ? 'заплыв' : (discipline.unitType ?? '—')} · Результат: {discipline.resultType === 'TIME' ? 'время' : (discipline.resultType ?? '—')}</p>
        <ProtocolDisciplineFacts settings={discipline.settingsJson} />
        <p>Подтверждено: {confirmed.length} · Остальных заявок: {registrations.length - confirmed.length}</p>
      </header>
      {!compatible ? <p className={styles.notice}>Версионируемая генерация рассчитана на INDIVIDUAL / HEAT. У этой дисциплины другой формат или тип. Не меняйте опубликованную дисциплину через SQL: сначала нужно подготовить совместимый шаблон. TIME может оставаться типом результата.</p> : null}
      {!published ? <p className={styles.notice}>Перед генерацией дисциплина должна быть опубликована.</p> : null}
      <section className={styles.card}>
        <h2>Настройки выбранного регламента</h2>
        <p>{configuration?.name ?? '—'} · версия {configuration?.versionNo ?? '—'}</p>
        {configuration?.settings ? <dl className={styles.rules}>
          <div><dt>Вместимость заплыва</dt><dd>{configuration.settings.capacity}</dd></div>
          <div><dt>Порядок дорожек</dt><dd>{configuration.settings.positionOrder.join(', ')}</dd></div>
          <div><dt>Порядок заплывов</dt><dd>{configuration.settings.heatOrder === 'SLOWEST_FIRST' ? 'От слабых к сильным' : 'От сильных к слабым'}</dd></div>
          <div><dt>Неполный заплыв</dt><dd>{configuration.settings.partialHeat === 'FIRST' ? 'В начале' : 'В конце'}</dd></div>
        </dl> : <p className={styles.notice}>В этой версии регламента нет настроек формирования. Создайте новый регламент с настройками или новую версию своего регламента. Уже созданное мероприятие останется на прежней версии.</p>}
        <ProtocolCategoryRules settings={configuration?.settings} />
        <NavLink className={styles.link} to="/organizer/regulations/create">Создать регламент</NavLink>
        {configuration ? <NavLink className={styles.link} to={`/organizer/regulations/create?sourceVersionId=${encodeURIComponent(configuration.regulationVersionId)}`}>Новая версия регламента (для владельца или администратора)</NavLink> : null}
        <p className={styles.hint}>Настройки закреплены за версией регламента мероприятия. Сервер не разрешает подменить их другим набором при генерации.</p>
        <button type="button" className={styles.primary} disabled={busy || !compatible || !published || !versionId || confirmed.length === 0} onClick={() => void handleGenerate()}>{busy ? 'Выполняем…' : protocol ? 'Переформировать протокол' : 'Сформировать протокол'}</button>
      </section>
      <section className={styles.card}>
        <h2>Подтверждённые заявки</h2>
        <ConfirmedRegistrationAudit registrations={confirmed} settings={configuration?.settings} disciplineSettings={discipline.settingsJson} />
        {!confirmed.length ? <p>Подтверждённых заявок нет. Завершите рассмотрение заявок перед генерацией.</p> : null}
      </section>
      <section className={styles.card}>
        <h2>Сохранённый протокол</h2>
        {!protocol ? <p>Протокол ещё не сформирован.</p> : <>
          <p>Заплывов: {protocol.unitCount} · Назначений: {protocol.assignmentCount}</p>
          <p className={styles.hint}>Коды категории и группы ниже взяты из сохранённых назначений. Описание категории расшифровано по загруженной конфигурации регламента. Предварительная проверка выше не заменяет сохранённые значения.</p>
          <p className={styles.hint}>Показатель посева отображается сразу после формирования протокола. После обновления страницы в этом столбце будет «—». Заявочное время по-прежнему видно рядом с именем участника. Если время не указано в заявке, там будет отметка «Без заявочного времени (NT)».</p>
          {[...protocol.units].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0)).map((unit) => <article className={styles.heat} key={unit.competitionUnitId}>
            <h3>{formatHeatLabel(unit.label)}</h3>
            <div className={styles.scroll}><table><thead><tr><th>Дорожка</th><th>Участник</th><th>Клуб</th><th>Показатель посева</th><th>Категория</th><th>Сохранённая группа распределения</th></tr></thead><tbody>
              {[...unit.assignments].sort((a, b) => (a.laneOrPosition ?? Number.MAX_SAFE_INTEGER) - (b.laneOrPosition ?? Number.MAX_SAFE_INTEGER)).map((assignment) => {
                const person = registrations.find((item) => item.id === assignment.registrationId)
                return <tr key={assignment.registrationId}><td>{assignment.laneOrPosition ?? '—'}</td><td>{participantName(person)}<small>{assignment.registrationId}</small><SavedParticipantFacts registration={person} /></td><td>{person?.participantSnapshot.clubName || '—'}</td><td>{assignment.seedMetric ?? '—'}</td><td><SavedCategoryDetails code={assignment.categoryCode} settings={configuration?.settings} /></td><td>{assignment.seedingGroupCode ?? '—'}</td></tr>
              })}
            </tbody></table></div>
          </article>)}
        </>}
      </section>
    </> : null}
  </section>
}
