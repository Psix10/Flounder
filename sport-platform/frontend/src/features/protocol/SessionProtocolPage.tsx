import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router'

import {
  getEventById,
  getEventDisciplines,
  type Event,
  type EventDiscipline,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  getSessionProtocol,
  type SessionProtocol,
} from '../../api/session-protocol.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { SessionProtocolForm } from './SessionProtocolForm'

import styles from './SessionProtocolPage.module.css'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия авторизации завершена. Войдите в аккаунт заново.'
    }

    if (error.status === 403) {
      return 'Нет доступа к программе этого мероприятия.'
    }

    return error.message || 'Не удалось загрузить программу.'
  }

  return error instanceof Error
    ? error.message
    : 'Не удалось загрузить программу.'
}

function formatTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'Некорректное время'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date)
}

type ContentProps = {
  eventId: string
  accessToken: string
}

function SessionProtocolContent({
  eventId,
  accessToken,
}: ContentProps) {
  const [event, setEvent] = useState<Event | null>(null)
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [protocol, setProtocol] = useState<SessionProtocol | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const controller = new AbortController()

    async function load() {
      try {
        const [eventData, disciplineData] = await Promise.all([
          getEventById(eventId, accessToken),
          getEventDisciplines(eventId, accessToken),
        ])

        let saved: SessionProtocol | null

        try {
          saved = await getSessionProtocol(
            eventId,
            accessToken,
            controller.signal,
          )
        } catch (failure) {
          if (failure instanceof ApiError && failure.status === 404) {
            saved = null
          } else {
            throw failure
          }
        }

        if (active) {
          setEvent(eventData)
          setDisciplines(disciplineData)
          setProtocol(saved)
        }
      } catch (failure) {
        if (active) {
          setError(errorMessage(failure))
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
      controller.abort()
    }
  }, [eventId, accessToken])

  const names = new Map(
    disciplines.map((discipline) => [discipline.id, discipline.name]),
  )

  const units = new Map(
    (protocol?.disciplines ?? []).flatMap((discipline) =>
      discipline.units.map((unit) => [unit.competitionUnitId, unit] as const),
    ),
  )

  const heats = [...(protocol?.schedule ?? [])].sort(
    (first, second) =>
      new Date(first.start).getTime() - new Date(second.start).getTime() ||
      first.savedHeatId.localeCompare(second.savedHeatId),
  )

  return (
    <>
      {isLoading ? <p role="status">Загружаем программу…</p> : null}

      {error ? (
        <p className={styles.error} role="alert">{error}</p>
      ) : null}

      {!isLoading && !error && event ? (
        <>
          <h2>{event.title}</h2>

          <SessionProtocolForm
            eventId={eventId}
            accessToken={accessToken}
            disciplines={disciplines}
            hasSnapshot={protocol !== null}
            onGenerated={setProtocol}
          />

          <section className={styles.card}>
            <h2>Последний сохранённый снимок</h2>

            <p className={styles.notice}>
              Снимок относится к набору дисциплин последней генерации.
              Он не объединяет независимые программы. Изменения заплывов
              после генерации могут не отражаться в этом снимке.
            </p>

            {!protocol ? (
              <p>Сохранённой программы пока нет.</p>
            ) : (
              <>
                <dl className={styles.summary}>
                  <div>
                    <dt>Ревизия</dt>
                    <dd>{protocol.revisionNo}</dd>
                  </div>
                  <div>
                    <dt>Дисциплин</dt>
                    <dd>{protocol.disciplines.length}</dd>
                  </div>
                  <div>
                    <dt>Заплывов</dt>
                    <dd>{protocol.schedule.length}</dd>
                  </div>
                </dl>

                <p className={styles.identifier}>
                  Идентификатор снимка: {protocol.snapshotId}
                </p>

                <p>
                  Часовой пояс отображения:{' '}
                  {Intl.DateTimeFormat().resolvedOptions().timeZone}.
                </p>

                {heats.length === 0 ? (
                  <p>В снимке нет заплывов.</p>
                ) : (
                  <div className={styles.scroll}>
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th scope="col">Начало</th>
                          <th scope="col">Окончание</th>
                          <th scope="col">Дисциплина</th>
                          <th scope="col">Заплыв</th>
                          <th scope="col">Участников</th>
                        </tr>
                      </thead>
                      <tbody>
                        {heats.map((heat) => {
                          const unit = units.get(heat.savedHeatId)

                          return (
                            <tr key={heat.savedHeatId}>
                              <td>
                                <time dateTime={heat.start}>
                                  {formatTime(heat.start)}
                                </time>
                              </td>
                              <td>
                                <time dateTime={heat.end}>
                                  {formatTime(heat.end)}
                                </time>
                              </td>
                              <td>
                                <NavLink
                                  className={styles.link}
                                  to={`/organizer/events/${encodeURIComponent(
                                    protocol.eventId,
                                  )}/disciplines/${encodeURIComponent(
                                    heat.disciplineId,
                                  )}/start-protocol`}
                                >
                                  {names.get(heat.disciplineId) ??
                                    heat.disciplineId}
                                </NavLink>
                              </td>
                              <td>
                                {unit?.label ??
                                  `Заплыв ${heat.sequenceNumber}`}
                              </td>
                              <td>
                                {unit?.assignments.length ?? '—'}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            )}
          </section>
        </>
      ) : null}
    </>
  )
}

export function SessionProtocolPage() {
  const { eventId } = useParams()
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [reload, setReload] = useState(0)

  return (
    <section className={styles.page}>
      <NavLink
        className={styles.link}
        to={
          eventId
            ? `/organizer/events/${encodeURIComponent(eventId)}`
            : '/organizer/events'
        }
      >
        К мероприятию
      </NavLink>

      <header className={styles.header}>
        <h1>Программа заплывов</h1>

        <button
          type="button"
          className={styles.button}
          onClick={() => {
            if (window.confirm(
              'Перезагрузить данные и очистить незаполненную или изменённую форму? ' +
              'Во время отправки программы не обновляйте страницу.',
            )) {
              setReload((value) => value + 1)
            }
          }}
        >
          Перезагрузить страницу программы
        </button>
      </header>

      {!eventId || !accessToken ? (
        <p className={styles.error} role="alert">
          {!eventId
            ? 'Не указан идентификатор мероприятия.'
            : 'Для работы с программой необходимо войти в аккаунт.'}
        </p>
      ) : (
        <SessionProtocolContent
          key={`${eventId}:${accessToken}:${reload}`}
          eventId={eventId}
          accessToken={accessToken}
        />
      )}
    </section>
  )
}