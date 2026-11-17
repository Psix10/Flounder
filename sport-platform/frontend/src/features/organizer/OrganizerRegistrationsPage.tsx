import { registrationStatusLabel } from '../registrations/registration-withdrawal.mjs'
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'

import { ApiError } from '../../api/http'
import {
  getEventById,
  getEventDisciplines,
  type EventResponse,
  type EventDiscipline,
} from '../../api/events.api'
import {
  getRegistrations,
  type GetRegistrationsOptions,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type {
  Registration,
  RegistrationStatus,
  ParticipantSnapshot,
} from '../registrations/registration.types'
import { RegistrationManagementActions } from './RegistrationManagementActions'
import styles from './OrganizerRegistrationsPage.module.css'

type RegistrationFilter = 'ALL' | RegistrationStatus

const FILTER_OPTIONS: {
  value: RegistrationFilter
  label: string
}[] = [
  { value: 'ALL', label: 'Все статусы' },
  { value: 'SUBMITTED', label: 'На рассмотрении' },
  { value: 'CONFIRMED', label: 'Подтверждённые' },
  { value: 'NEEDS_CORRECTION', label: 'Требуют уточнения' },
  { value: 'REJECTED', label: 'Отклонённые' },
  { value: 'WITHDRAWN', label: 'Снятые с участия' },
]

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в аккаунт повторно.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для просмотра заявок.'
    }

    return error.message || fallback
  }

  return fallback
}


function registrationStatusClassName(
  status: RegistrationStatus,
): string {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    NEEDS_CORRECTION: styles.statusCorrection,
    REJECTED: styles.statusRejected,
    CANCELLED: styles.statusCancelled,
    WITHDRAWN: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status] ?? ''}`
}

function formatDate(value: string | null): string {
  if (!value) {
    return 'Не указано'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'Не указано'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(date)
}

function getSnapshotString(
  snapshot: ParticipantSnapshot | null | undefined,
  property: keyof ParticipantSnapshot,
): string {
  const value = snapshot?.[property]
  return typeof value === 'string' ? value.trim() : ''
}

function getParticipantName(registration: Registration): string {
  const snapshot = registration.participantSnapshot

  return (
    [
      getSnapshotString(snapshot, 'lastName'),
      getSnapshotString(snapshot, 'firstName'),
      getSnapshotString(snapshot, 'middleName'),
    ]
      .filter(Boolean)
      .join(' ') || 'Участник без указания ФИО'
  )
}

function getParticipantSecondaryInfo(
  registration: Registration,
): string {
  const snapshot = registration.participantSnapshot

  return [
    getSnapshotString(snapshot, 'clubName'),
    getSnapshotString(snapshot, 'city'),
  ]
    .filter(Boolean)
    .join(' · ')
}

function getParticipantGenderLabel(
  registration: Registration,
): string {
  const gender = registration.participantSnapshot?.gender

  if (gender === 'MALE') {
    return 'Мужской'
  }

  if (gender === 'FEMALE') {
    return 'Женский'
  }

  if (gender === null || gender === undefined || gender === '') {
    return 'Не указан'
  }

  return 'Некорректное значение'
}

function getSeedTimeLabel(registration: Registration): string {
  const meta: unknown = registration.registrationMeta

  if (meta === undefined || meta === null) {
    return 'Без заявочного времени (NT)'
  }

  if (typeof meta !== 'object' || Array.isArray(meta)) {
    return 'Некорректные данные заявки'
  }

  if (!('seedTimeSeconds' in meta)) {
    return 'Без заявочного времени (NT)'
  }

  const value = meta.seedTimeSeconds

  if (value === undefined || value === null) {
    return 'Без заявочного времени (NT)'
  }

  if (
    typeof value !== 'number' ||
    !Number.isFinite(value) ||
    value <= 0
  ) {
    return 'Некорректное заявочное время'
  }

  const scaled = value * 100
  const totalHundredths = Math.round(scaled)

  if (
    !Number.isSafeInteger(totalHundredths) ||
    totalHundredths <= 0 ||
    Math.abs(scaled - totalHundredths) > 0.000001
  ) {
    return 'Некорректное заявочное время'
  }

  const minutes = Math.floor(totalHundredths / 6000)
  const seconds = Math.floor((totalHundredths % 6000) / 100)
  const hundredths = totalHundredths % 100

  return (
    `${String(minutes).padStart(2, '0')}:` +
    `${String(seconds).padStart(2, '0')}.` +
    String(hundredths).padStart(2, '0')
  )
}

export function OrganizerRegistrationsPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  if (!eventId || !accessToken) {
    return (
      <section className={styles.page}>
        <Link className={styles.backLink} to="/organizer/events">
          ← К моим событиям
        </Link>

        <p className={styles.errorMessage} role="alert">
          {!eventId
            ? 'Не указан идентификатор мероприятия.'
            : 'Для просмотра заявок необходимо войти в аккаунт.'}
        </p>
      </section>
    )
  }

  return (
    <OrganizerRegistrationsContent
      key={`${eventId}:${accessToken}`}
      eventId={eventId}
      accessToken={accessToken}
    />
  )
}

function OrganizerRegistrationsContent({
  eventId,
  accessToken,
}: {
  eventId: string
  accessToken: string
}) {
  const [event, setEvent] = useState<EventResponse | null>(null)
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [registrations, setRegistrations] = useState<Registration[]>([])

  const [statusFilter, setStatusFilter] =
    useState<RegistrationFilter>('SUBMITTED')
  const [disciplineFilter, setDisciplineFilter] = useState('')

  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    let active = true

    setIsLoading(true)
    setErrorMessage(null)

    async function load() {
      const options: GetRegistrationsOptions = { eventId }

      if (statusFilter !== 'ALL') {
        options.status = statusFilter
      }

      try {
        const [
          loadedEvent,
          loadedDisciplines,
          loadedRegistrations,
        ] = await Promise.all([
          getEventById(eventId, accessToken),
          getEventDisciplines(eventId, accessToken),
          getRegistrations(accessToken, options),
        ])

        if (!active) {
          return
        }

        setEvent(loadedEvent)
        setDisciplines(loadedDisciplines)
        setRegistrations(loadedRegistrations)

        setDisciplineFilter((current) =>
          current &&
          !loadedDisciplines.some(
            (discipline) => discipline.id === current,
          )
            ? ''
            : current,
        )
      } catch (error) {
        if (active) {
          setErrorMessage(
            getErrorMessage(
              error,
              'Не удалось загрузить событие, дисциплины и заявки.',
            ),
          )
        }
      } finally {
        if (active) {
          setIsLoading(false)
        }
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [eventId, accessToken, statusFilter, refresh])

  const disciplineNameById = useMemo(
    () =>
      new Map(
        disciplines.map((discipline) => [
          discipline.id,
          discipline.name,
        ]),
      ),
    [disciplines],
  )

  const visibleRegistrations = useMemo(
    () =>
      disciplineFilter
        ? registrations.filter(
            (registration) =>
              registration.eventDisciplineId === disciplineFilter,
          )
        : registrations,
    [disciplineFilter, registrations],
  )

  const eventBase =
    `/organizer/events/${encodeURIComponent(eventId)}`

  function handleReturnedForCorrection(updated: Registration) {
    setRegistrations((current) =>
      current
        .map((registration) =>
          registration.id === updated.id
            ? updated
            : registration,
        )
        .filter(
          (registration) =>
            statusFilter === 'ALL' ||
            registration.status === statusFilter,
        ),
    )

    setSuccessMessage(
      `Заявка участника «${getParticipantName(updated)}» возвращена ` +
        'на уточнение. Участник должен отправить исправления, ' +
        'после чего потребуется повторное рассмотрение. ' +
        'Для поиска заявки выберите статус «Требуют уточнения».',
    )

    setRefresh((current) => current + 1)
  }

  function handleWithdrawn(updated: Registration) {
    setRegistrations((current) => current
      .map((registration) => registration.id === updated.id ? updated : registration)
      .filter((registration) => statusFilter === 'ALL' || registration.status === statusFilter))
    setSuccessMessage(
      `Заявка участника «${getParticipantName(updated)}» снята с участия. ` +
      'Для поиска выберите статус «Снятые с участия». ' +
      'Связанный допустимый неоплаченный ручной платёж отменён сервером, если он существовал.',
    )
    setRefresh((current) => current + 1)
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to={eventBase}>
        ← К управлению мероприятием
      </Link>

      <p className={styles.eyebrow}>Панель организатора</p>

      <h1 className={styles.title}>
        {event ? `Заявки: ${event.title}` : 'Заявки события'}
      </h1>

      <p className={styles.description}>
        Просматривайте заявки участников, фильтруйте их по дисциплинам
        и принимайте решение по каждой заявке. Подтверждённую заявку
        можно вернуть на уточнение или снять с участия, если это допускают проверки сервера.
      </p>

      <div>
        <button
          className={styles.reviewLink}
          type="button"
          disabled={isLoading}
          onClick={() => {
            setSuccessMessage(null)
            setRefresh((current) => current + 1)
          }}
        >
          {isLoading ? 'Обновляем…' : 'Обновить заявки'}
        </button>
      </div>

      {successMessage ? (
        <div className={styles.stateCard} role="status">
          <p>{successMessage}</p>
        </div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {isLoading ? (
        <div className={styles.stateCard} role="status">
          Загружаем событие, дисциплины и заявки…
        </div>
      ) : null}

      {!isLoading && !errorMessage && event ? (
        <>
          <div className={styles.filtersCard}>
            <label className={styles.field}>
              <span>Дисциплина</span>

              <select
                value={disciplineFilter}
                onChange={(changeEvent) =>
                  setDisciplineFilter(changeEvent.target.value)
                }
              >
                <option value="">Все дисциплины</option>

                {disciplines.map((discipline) => (
                  <option key={discipline.id} value={discipline.id}>
                    {discipline.name}
                  </option>
                ))}
              </select>
            </label>

            <label className={styles.field}>
              <span>Статус заявки</span>

              <select
                value={statusFilter}
                onChange={(changeEvent) => {
                  const value = changeEvent.target.value

                  if (
                    FILTER_OPTIONS.some(
                      (option) => option.value === value,
                    )
                  ) {
                    setStatusFilter(value as RegistrationFilter)
                  }
                }}
              >
                {FILTER_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <div className={styles.resultsCount}>
              Найдено заявок:{' '}
              <strong>{visibleRegistrations.length}</strong>
            </div>
          </div>

          {visibleRegistrations.length === 0 ? (
            <div className={styles.stateCard}>
              <h2>Заявок не найдено</h2>
              <p>
                Попробуйте изменить фильтры или обновите список
                после поступления новых заявок.
              </p>
            </div>
          ) : (
            <div className={styles.registrationsList}>
              {visibleRegistrations.map((registration) => {
                const participantName =
                  getParticipantName(registration)

                const secondaryInfo =
                  getParticipantSecondaryInfo(registration)

                const disciplineName = disciplineNameById.get(
                  registration.eventDisciplineId,
                )

                return (
                  <article
                    className={styles.registrationCard}
                    key={registration.id}
                  >
                    <div className={styles.cardHeader}>
                      <div>
                        <p className={styles.cardLabel}>
                          {disciplineName ??
                            'Название дисциплины недоступно'}
                        </p>

                        {!disciplineName ? (
                          <p className={styles.secondaryInfo}>
                            ID дисциплины:{' '}
                            {registration.eventDisciplineId}
                          </p>
                        ) : null}

                        <h2 className={styles.participantName}>
                          {participantName}
                        </h2>

                        {secondaryInfo ? (
                          <p className={styles.secondaryInfo}>
                            {secondaryInfo}
                          </p>
                        ) : null}

                        <p className={styles.secondaryInfo}>
                          Пол в снимке заявки:{' '}
                          {getParticipantGenderLabel(registration)}
                        </p>

                        <p className={styles.secondaryInfo}>
                          Заявочное время:{' '}
                          {getSeedTimeLabel(registration)}
                        </p>

                        {registration.reviewNote ? (
                          <p className={styles.secondaryInfo}>
                            Комментарий организатора:{' '}
                            {registration.reviewNote}
                          </p>
                        ) : null}
                      </div>

                      <span
                        className={registrationStatusClassName(
                          registration.status,
                        )}
                      >
                        {registrationStatusLabel(registration.status)}
                      </span>
                    </div>

                    <div className={styles.cardFooter}>
                      <span>
                        Подана: {formatDate(registration.submittedAt)}
                      </span>

                      <Link
                        className={styles.reviewLink}
                        to={
                          '/operator/registrations/' +
                          encodeURIComponent(registration.id)
                        }
                      >
                        {registration.status === 'SUBMITTED'
                          ? 'Рассмотреть заявку'
                          : 'Открыть заявку'}
                      </Link>
                    </div>

                    {registration.status === 'CONFIRMED' ? (
                      <RegistrationManagementActions
                        key={`${registration.id}:${registration.updatedAt}`}
                        registration={registration}
                        accessToken={accessToken}
                        onCorrection={handleReturnedForCorrection}
                        onWithdrawal={handleWithdrawn}
                        onRefresh={() => setRefresh((current) => current + 1)}
                      />
                    ) : null}
                  </article>
                )
              })}
            </div>
          )}
        </>
      ) : null}
    </section>
  )
}
