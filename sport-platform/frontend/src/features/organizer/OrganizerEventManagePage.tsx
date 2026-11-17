// EVENT_OPERATORS_LINK_V1
// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useParams } from 'react-router'

import {
  closeEventRegistration,
  completeEvent,
  getEventById,
  getEventDisciplines,
  openEventRegistration,
  publishEvent,
  publishEventDiscipline,
  type Event,
  type EventDiscipline,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import { CreateEventDisciplineForm } from './CreateEventDisciplineForm'
import { OrganizerEventJourney } from './OrganizerEventJourney'
import {
  getDisciplineStatusLabel,
  getDisciplineStatusTone,
} from './discipline-status'
import {
  getEventStatusLabel,
  getEventStatusTone,
  normalizeEventStatus,
} from './event-status'
import { DemoRegistrationPanel } from '../protocol/DemoRegistrationPanel'
// DEMO_DATA_PANEL_V1
import styles from './OrganizerEventManagePage.module.css'

type EventAction =
  | 'publish'
  | 'open-registration'
  | 'close-registration'
  | 'complete'

const actionLabels: Record<
  EventAction,
  { idle: string; busy: string; success: string }
> = {
  publish: {
    idle: 'Опубликовать мероприятие',
    busy: 'Публикуем…',
    success: 'Мероприятие опубликовано.',
  },
  'open-registration': {
    idle: 'Открыть регистрацию',
    busy: 'Открываем…',
    success: 'Регистрация открыта.',
  },
  'close-registration': {
    idle: 'Закрыть регистрацию',
    busy: 'Закрываем…',
    success: 'Регистрация закрыта.',
  },
  complete: {
    idle: 'Завершить мероприятие',
    busy: 'Завершаем…',
    success:
      'Мероприятие завершено. Подтверждение итогов проверяется отдельно по каждой дисциплине.',
  },
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в аккаунт повторно.'
    }

    if (error.status === 403) {
      return 'Нет доступа к управлению мероприятием.'
    }

    return error.message || fallback
  }

  return fallback
}

function statusClassName(status: string): string {
  switch (getEventStatusTone(status)) {
    case 'draft':
      return `${styles.statusBadge} ${styles.statusDraft}`
    case 'published':
      return `${styles.statusBadge} ${styles.statusPublished}`
    case 'open':
      return `${styles.statusBadge} ${styles.statusOpen}`
    case 'closed':
      return `${styles.statusBadge} ${styles.statusClosed}`
    case 'live':
      return `${styles.statusBadge} ${styles.statusLive}`
    case 'completed':
      return `${styles.statusBadge} ${styles.statusCompleted}`
    case 'archived':
      return `${styles.statusBadge} ${styles.statusArchived}`
    default:
      return `${styles.statusBadge} ${styles.statusNeutral}`
  }
}

function disciplineStatusClassName(status?: string | null): string {
  switch (getDisciplineStatusTone(status)) {
    case 'draft':
      return `${styles.statusBadge} ${styles.statusDraft}`
    case 'published':
      return `${styles.statusBadge} ${styles.statusPublished}`
    case 'archived':
      return `${styles.statusBadge} ${styles.statusArchived}`
    default:
      return `${styles.statusBadge} ${styles.statusNeutral}`
  }
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return '—'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function getAvailableAction(status: string): EventAction | null {
  switch (status) {
    case 'DRAFT':
      return 'publish'
    case 'PUBLISHED':
      return 'open-registration'
    case 'REGISTRATION_OPEN':
      return 'close-registration'
    case 'REGISTRATION_CLOSED':
    case 'LIVE':
      return 'complete'
    default:
      return null
  }
}

export function OrganizerEventManagePage() {
  const { eventId } = useParams()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  return (
    <OrganizerEventManageContent
      key={`${eventId ?? 'missing'}:${accessToken ?? 'anonymous'}`}
      eventId={eventId}
      accessToken={accessToken}
    />
  )
}

function OrganizerEventManageContent({
  eventId,
  accessToken,
}: {
  eventId: string | undefined
  accessToken: string | undefined
}) {
  const [event, setEvent] = useState<Event | null>(null)
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [publishingId, setPublishingId] = useState<string | null>(null)
  const [eventAction, setEventAction] = useState<EventAction | null>(null)
  const [isCreatingDiscipline, setIsCreatingDiscipline] = useState(false)
  const [isDemoBusy, setIsDemoBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [needsRefresh, setNeedsRefresh] = useState(false)

  const mounted = useRef(false)
  const actionLock = useRef(false)
  const disciplineCreationBusy = useRef(false)
  const refreshRequired = useRef(false)
  const loadingRef = useRef(true)

  useEffect(() => {
    mounted.current = true

    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    let active = true

    loadingRef.current = true
    setIsLoading(true)
    setErrorMessage(null)
    setActionMessage(null)

    if (!eventId || !accessToken) {
      loadingRef.current = false
      setIsLoading(false)
      setErrorMessage(
        !eventId
          ? 'Не указан идентификатор мероприятия.'
          : 'Для управления мероприятием необходимо войти в аккаунт.',
      )

      return () => {
        active = false
      }
    }

    const resolvedEventId = eventId
    const token = accessToken

    async function loadData() {
      try {
        const [eventData, disciplinesData] = await Promise.all([
          getEventById(resolvedEventId, token),
          getEventDisciplines(resolvedEventId, token),
        ])

        if (!active) {
          return
        }

        setEvent(eventData)
        setDisciplines(disciplinesData)
        refreshRequired.current = false
        setNeedsRefresh(false)
      } catch (error) {
        if (active) {
          refreshRequired.current = true
          setNeedsRefresh(true)
          setErrorMessage(
            getErrorMessage(error, 'Не удалось загрузить мероприятие.'),
          )
        }
      } finally {
        if (active) {
          loadingRef.current = false
          setIsLoading(false)
        }
      }
    }

    void loadData()

    return () => {
      active = false
    }
  }, [eventId, accessToken, refresh])

  const isActionBusy =
    isLoading ||
    needsRefresh ||
    eventAction !== null ||
    publishingId !== null ||
    isCreatingDiscipline ||
    isDemoBusy

  const canRefresh =
    Boolean(eventId && accessToken) &&
    !isLoading &&
    eventAction === null &&
    publishingId === null &&
    !isCreatingDiscipline &&
    !isDemoBusy

  function refreshState() {
    if (
      !canRefresh ||
      loadingRef.current ||
      actionLock.current ||
      disciplineCreationBusy.current
    ) {
      return
    }

    loadingRef.current = true
    setIsLoading(true)
    setRefresh((current) => current + 1)
  }

  function requireRefresh() {
    refreshRequired.current = true
    setNeedsRefresh(true)
  }

  function operationBlocked(): boolean {
    return (
      !mounted.current ||
      loadingRef.current ||
      refreshRequired.current ||
      actionLock.current ||
      disciplineCreationBusy.current ||
      isActionBusy
    )
  }

  async function handlePublishDiscipline(disciplineId: string) {
    if (!eventId || !accessToken || !event || operationBlocked()) {
      return
    }

    const discipline = disciplines.find(
      (item) => item.id === disciplineId,
    )

    if (discipline?.status?.trim().toUpperCase() !== 'DRAFT') {
      return
    }

    actionLock.current = true
    setPublishingId(disciplineId)
    setErrorMessage(null)
    setActionMessage(null)

    try {
      const updatedDiscipline = await publishEventDiscipline(
        eventId,
        disciplineId,
        accessToken,
      )

      if (!mounted.current) {
        return
      }

      setDisciplines((current) =>
        current.map((item) =>
          item.id === disciplineId ? updatedDiscipline : item,
        ),
      )

      setActionMessage('Дисциплина опубликована.')
    } catch (error) {
      if (mounted.current) {
        setErrorMessage(
          getErrorMessage(
            error,
            'Не удалось получить подтверждение публикации дисциплины.',
          ),
        )
        requireRefresh()
      }
    } finally {
      actionLock.current = false

      if (mounted.current) {
        setPublishingId(null)
      }
    }
  }

  async function handleEventAction(action: EventAction) {
    if (!eventId || !accessToken || !event || operationBlocked()) {
      return
    }

    if (getAvailableAction(normalizeEventStatus(event.status)) !== action) {
      return
    }

    const publishedCount = disciplines.filter(
      (discipline) =>
        discipline.status?.trim().toUpperCase() === 'PUBLISHED',
    ).length

    const confirmations: Record<EventAction, string> = {
      publish:
        `Опубликовать мероприятие «${event.title}»?\n\n` +
        `Дисциплин: ${disciplines.length}. ` +
        `Опубликовано: ${publishedCount}.\n\n` +
        'В текущем интерфейсе после публикации события ' +
        'добавление новых дисциплин будет недоступно. ' +
        'Проверьте состав программы перед продолжением.',

      'open-registration':
        `Открыть регистрацию на «${event.title}»?\n\n` +
        `Опубликованных дисциплин: ${publishedCount}.\n\n` +
        'Операция изменит статус события. ' +
        'Допуск к подаче заявок также зависит от серверных условий.',

      'close-registration':
        `Закрыть регистрацию на «${event.title}»?\n\n` +
        'В этом экране нет действия для повторного открытия регистрации. ' +
        'Перед продолжением проверьте поступившие заявки.',

      complete:
        `Завершить мероприятие «${event.title}»?\n\n` +
        'Операция изменит статус события, но сама по себе ' +
        'не подтверждает итоговые таблицы дисциплин. ' +
        'В этом экране нет действия для возврата события ' +
        'в предыдущий статус.',
    }

    if (!window.confirm(confirmations[action])) {
      return
    }

    actionLock.current = true
    setEventAction(action)
    setErrorMessage(null)
    setActionMessage(null)

    try {
      let updatedEvent: Event

      switch (action) {
        case 'publish':
          updatedEvent = await publishEvent(eventId, accessToken)
          break
        case 'open-registration':
          updatedEvent = await openEventRegistration(eventId, accessToken)
          break
        case 'close-registration':
          updatedEvent = await closeEventRegistration(eventId, accessToken)
          break
        case 'complete':
          updatedEvent = await completeEvent(eventId, accessToken)
          break
      }

      if (!mounted.current) {
        return
      }

      setEvent(updatedEvent)
      setActionMessage(actionLabels[action].success)
    } catch (error) {
      if (mounted.current) {
        setErrorMessage(
          getErrorMessage(
            error,
            'Не удалось получить подтверждение изменения статуса.',
          ),
        )
        requireRefresh()
      }
    } finally {
      actionLock.current = false

      if (mounted.current) {
        setEventAction(null)
      }
    }
  }

  function handleDisciplineBusyChange(busy: boolean) {
    disciplineCreationBusy.current = busy

    if (mounted.current) {
      setIsCreatingDiscipline(busy)
    }
  }

  function handleDisciplineCreated(discipline: EventDiscipline) {
    if (!mounted.current) {
      return
    }

    setDisciplines((current) => {
      const exists = current.some((item) => item.id === discipline.id)

      return exists
        ? current.map((item) =>
            item.id === discipline.id ? discipline : item,
          )
        : [...current, discipline]
    })

    setErrorMessage(null)
    setActionMessage(
      'Дисциплина добавлена. Теперь её можно опубликовать.',
    )
  }

  const eventStatus = normalizeEventStatus(event?.status)
  const availableAction = getAvailableAction(eventStatus)

  const eventBase = event
    ? `/organizer/events/${encodeURIComponent(event.id)}`
    : ''

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/organizer/events">
        ← К мероприятиям
      </NavLink>

      <div className={styles.actionRow}>
        <button
          type="button"
          className={styles.secondaryButton}
          disabled={!canRefresh}
          onClick={refreshState}
        >
          {isLoading ? 'Обновляем…' : 'Обновить состояние'}
        </button>
      </div>

      {isLoading ? (
        <div className={styles.stateCard} role="status">
          Загружаем мероприятие…
        </div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {needsRefresh ? (
        <p className={styles.stateCard} role="status">
          Перед следующей операцией обновите состояние.
          Данные могли измениться. Предыдущий запрос изменения
          мог быть выполнен сервером, даже если подтверждение не получено.
        </p>
      ) : null}

      {actionMessage ? (
        <p className={styles.successMessage} role="status">
          {actionMessage}
        </p>
      ) : null}

      {!isLoading && event ? (
        <>
          <header className={styles.hero}>
            <div>
              <p className={styles.eyebrow}>Управление мероприятием</p>
              <h1 className={styles.title}>{event.title}</h1>

              <div className={styles.meta}>
                <span className={statusClassName(event.status)}>
                  {getEventStatusLabel(event.status)}
                </span>
                <span>Начало: {formatDate(event.eventStartAt)}</span>
                <span>Окончание: {formatDate(event.eventEndAt)}</span>
              </div>

              {event.description ? (
                <p className={styles.description}>
                  {event.description}
                </p>
              ) : null}
            </div>

            <div className={styles.heroActions}>
              <NavLink className={styles.secondaryLink} to={`${eventBase}/operators`}>
                Операторы мероприятия
              </NavLink>

              <NavLink
                className={styles.secondaryLink}
                to={`${eventBase}/registrations`}
              >
                Заявки
              </NavLink>

              {event.publicSlug ? (
                <NavLink
                  className={styles.secondaryLink}
                  to={`/events/${encodeURIComponent(event.publicSlug)}`}
                >
                  Открыть страницу
                </NavLink>
              ) : null}
            </div>
          </header>

          <OrganizerEventJourney
            key={`${event.id}:${refresh}`}
            event={event}
            disciplines={disciplines}
          />

          <section id="event-status" className={styles.card}>
            <h2 className={styles.cardTitle}>Статус мероприятия</h2>

            {eventStatus === 'DRAFT' ? (
              <div className={styles.stateCard}>
                Перед публикацией проверьте состав дисциплин.
                В текущем интерфейсе после публикации события
                добавление дисциплин недоступно.
              </div>
            ) : null}

            {eventStatus === 'PUBLISHED' ? (
              <div className={styles.stateCard}>
                Перед открытием регистрации проверьте,
                что нужные дисциплины опубликованы.
              </div>
            ) : null}

            {eventStatus === 'REGISTRATION_CLOSED' ||
            eventStatus === 'LIVE' ? (
              <div className={styles.stateCard}>
                Завершение мероприятия меняет его статус,
                но не подтверждает итоговые таблицы дисциплин.
              </div>
            ) : null}

            <div className={styles.actionRow}>
              {availableAction ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={isActionBusy}
                  onClick={() =>
                    void handleEventAction(availableAction)
                  }
                >
                  {eventAction === availableAction
                    ? actionLabels[availableAction].busy
                    : actionLabels[availableAction].idle}
                </button>
              ) : (
                <p className={styles.disciplineMeta}>
                  Для текущего статуса на этом экране
                  нет следующего перехода.
                </p>
              )}
            </div>
          </section>

          <section id="event-disciplines" className={styles.card}>
            <h2 className={styles.cardTitle}>Дисциплины</h2>

            {eventStatus === 'DRAFT' && accessToken ? (
              <CreateEventDisciplineForm
                key={`${event.id}:${event.sportId}`}
                eventId={event.id}
                sportId={event.sportId}
                accessToken={accessToken}
                disabled={
                  isLoading ||
                  needsRefresh ||
                  eventAction !== null ||
                  publishingId !== null ||
                  isDemoBusy
                }
                existingCodes={disciplines.map(
                  (discipline) => discipline.code ?? '',
                )}
                onBusyChange={handleDisciplineBusyChange}
                onCreated={handleDisciplineCreated}
              />
            ) : eventStatus !== 'DRAFT' ? (
              <div className={styles.stateCard}>
                Добавление дисциплин доступно только в черновике
                мероприятия. Программу нужно настроить до публикации.
              </div>
            ) : null}

            {disciplines.length === 0 ? (
              <div className={styles.stateCard}>
                Для этого мероприятия пока нет дисциплин.
              </div>
            ) : (
              <div className={styles.disciplineList}>
                {disciplines.map((discipline) => {
                  const disciplineStatus = (
                    discipline.status || 'UNKNOWN'
                  ).trim().toUpperCase()

                  const disciplineBase =
                    `${eventBase}/disciplines/` +
                    encodeURIComponent(discipline.id)

                  const resultsSearch = new URLSearchParams({
                    disciplineId: discipline.id,
                    eventId: event.id,
                  }).toString()

                  return (
                    <article
                      key={discipline.id}
                      className={styles.disciplineCard}
                    >
                      <div className={styles.disciplineHeader}>
                        <div>
                          <h3 className={styles.disciplineTitle}>
                            {formatHeatLabel(discipline.name)}
                          </h3>

                          <p className={styles.disciplineMeta}>
                            Код: {discipline.code || '—'} · Формат:{' '}
                            {discipline.competitionFormat}
                          </p>
                        </div>

                        <span
                          className={disciplineStatusClassName(
                            disciplineStatus,
                          )}
                        >
                          {getDisciplineStatusLabel(disciplineStatus)}
                        </span>
                      </div>

                      <div className={styles.disciplineFooter}>
                        <span>
                          Лимит участников:{' '}
                          {discipline.participantLimit ?? '—'}
                        </span>

                        <NavLink
                          className={styles.secondaryLink}
                          to={`${disciplineBase}/standings`}
                        >
                          Зачёт и подтверждение итогов
                        </NavLink>

                        <NavLink
                          className={styles.secondaryLink}
                          to={`${disciplineBase}/start-protocol`}
                        >
                          Стартовый протокол
                        </NavLink>

                        <NavLink
                          className={styles.secondaryLink}
                          to={`/operator/results?${resultsSearch}`}
                        >
                          Ввод результатов
                        </NavLink>

                        {disciplineStatus === 'DRAFT' ? (
                          <button
                            type="button"
                            className={styles.primaryButton}
                            disabled={isActionBusy}
                            onClick={() =>
                              void handlePublishDiscipline(discipline.id)
                            }
                          >
                            {publishingId === discipline.id
                              ? 'Публикуем…'
                              : 'Опубликовать'}
                          </button>
                        ) : null}
                      </div>

                      {eventStatus === 'DRAFT' && accessToken &&
                      discipline.competitionFormat === 'INDIVIDUAL' &&
                      discipline.unitType === 'HEAT' ? (
                        <DemoRegistrationPanel
                          key={`demo:${event.id}:${discipline.id}:${refresh}`}
                          eventId={event.id}
                          disciplineId={discipline.id}
                          token={accessToken}
                          disabled={isActionBusy}
                          onAcquire={() => {
                            if (operationBlocked()) return false
                            actionLock.current = true
                            setIsDemoBusy(true)
                            return true
                          }}
                          onRelease={() => {
                            actionLock.current = false
                            if (mounted.current) setIsDemoBusy(false)
                          }}
                          onCreated={() => {
                            if (!mounted.current) return
                            loadingRef.current = true
                            setIsLoading(true)
                            setRefresh((value) => value + 1)
                          }}
                        />
                      ) : null}
                    </article>
                  )
                })}
              </div>
            )}
          </section>
        </>
      ) : null}
    </section>
  )
}