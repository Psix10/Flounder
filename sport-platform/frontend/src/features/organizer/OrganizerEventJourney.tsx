import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'

import type { Event, EventDiscipline } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { getSessionProtocol } from '../../api/session-protocol.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { normalizeEventStatus } from './event-status'

import styles from './OrganizerEventJourney.module.css'

type Props = {
  event: Event
  disciplines: EventDiscipline[]
}

type SnapshotState =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'saved'; revision: number; heats: number }
  | { kind: 'error'; message: string }

export function OrganizerEventJourney({
  event,
  disciplines,
}: Props) {
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [snapshot, setSnapshot] = useState<SnapshotState>({
    kind: 'loading',
  })

  useEffect(() => {
    let active = true
    const controller = new AbortController()

    setSnapshot({ kind: 'loading' })

    if (!accessToken) {
      setSnapshot({
        kind: 'error',
        message: 'Для проверки программы требуется авторизация.',
      })

      return () => {
        active = false
        controller.abort()
      }
    }

    const token = accessToken

    async function loadSnapshot() {
      try {
        const result = await getSessionProtocol(
          event.id,
          token,
          controller.signal,
        )

        if (active) {
          setSnapshot({
            kind: 'saved',
            revision: result.revisionNo,
            heats: result.schedule.length,
          })
        }
      } catch (failure) {
        if (!active) {
          return
        }

        if (failure instanceof ApiError && failure.status === 404) {
          setSnapshot({ kind: 'missing' })
        } else {
          setSnapshot({
            kind: 'error',
            message:
              failure instanceof ApiError && failure.status === 403
                ? 'Нет доступа к чтению программы.'
                : 'Не удалось проверить наличие программы.',
          })
        }
      }
    }

    void loadSnapshot()

    return () => {
      active = false
      controller.abort()
    }
  }, [event.id, accessToken])

  const status = normalizeEventStatus(event.status)
  const base = `/organizer/events/${encodeURIComponent(event.id)}`

  const published = disciplines.filter(
    (discipline) => discipline.status?.toUpperCase() === 'PUBLISHED',
  )

  const supported = published.filter(
    (discipline) =>
      discipline.competitionFormat === 'INDIVIDUAL' &&
      discipline.unitType === 'HEAT',
  )

  let nextTitle = 'Проверьте состояние соревнования'
  let nextText =
    'Откройте нужный этап ниже. Доступность операций проверяется сервером.'
  let nextTarget = '#event-status'

  if (status === 'DRAFT') {
    nextTitle = 'Подготовьте событие к публикации'
    nextText =
      disciplines.length === 0
        ? 'Добавьте дисциплины. Их состав нужно настроить до публикации события.'
        : 'Проверьте состав дисциплин и настройки регламента. Затем опубликуйте событие в блоке статуса.'
    nextTarget =
      disciplines.length === 0 ? '#event-disciplines' : '#event-status'
  } else if (status === 'PUBLISHED') {
    nextTitle = 'Подготовьте приём заявок'
    nextText =
      'Проверьте публикацию дисциплин и откройте регистрацию в блоке статуса мероприятия.'
  } else if (status === 'REGISTRATION_OPEN') {
    nextTitle = 'Проверьте поступившие заявки'
    nextText =
      'Проверьте участников, запросите исправления при необходимости и подтвердите подходящие заявки.'
    nextTarget = `${base}/registrations`
  } else if (status === 'REGISTRATION_CLOSED') {
    nextTitle = 'Проверьте допуск и подготовьте программу'
    nextText =
      'Убедитесь, что нужные заявки подтверждены. Затем настройте порядок дисциплин и временные параметры.'
    nextTarget =
      supported.length > 0
        ? `${base}/session-protocol`
        : '#event-disciplines'
  } else if (status === 'LIVE') {
    nextTitle = 'Контролируйте внесение результатов'
    nextText =
      'Открывайте результаты по дисциплинам. Наличие расписания само по себе не означает готовность итогов.'
    nextTarget = '#event-disciplines'
  } else if (status === 'COMPLETED') {
    nextTitle = 'Проверьте итоговые таблицы'
    nextText =
      'Откройте зачёт каждой дисциплины и проверьте подтверждение итогов. Статус события не заменяет эту проверку.'
    nextTarget = '#event-disciplines'
  } else if (status === 'ARCHIVED') {
    nextTitle = 'Просмотрите сохранённые материалы'
    nextText =
      'Откройте программу и итоговые таблицы. Архивный статус не является проверкой их полноты.'
    nextTarget = '#event-disciplines'
  }

  const stageLink = (target: string, text: string) =>
    target.startsWith('#') ? (
      <a className={styles.link} href={target}>{text}</a>
    ) : (
      <NavLink className={styles.link} to={target}>{text}</NavLink>
    )

  return (
    <section className={styles.panel} aria-labelledby="journey-heading">
      <header>
        <h2 id="journey-heading">Путь организатора</h2>
        <p>
          Этапы работы с событием. Здесь нет ручных отметок о готовности:
          показаны доступные данные и переходы к проверке.
        </p>
      </header>

      <div className={styles.next}>
        <h3>{nextTitle}</h3>
        <p>{nextText}</p>
        {stageLink(nextTarget, 'Перейти к действию')}
      </div>

      <ol className={styles.stages}>
        <li>
          <h3>Событие и регламент</h3>
          <p>
            {event.regulationVersionId
              ? 'Версия регламента указана. Совместимость и содержание правил нужно проверить отдельно.'
              : 'В полученных данных не указана версия регламента.'}
          </p>
          {stageLink('#event-status', 'Открыть управление статусом')}
        </li>

        <li>
          <h3>Дисциплины</h3>
          <p>
            Всего: {disciplines.length}. Опубликовано: {published.length}.
            Для текущего планировщика подходят: {supported.length}.
          </p>
          {stageLink('#event-disciplines', 'Проверить дисциплины')}
        </li>

        <li>
          <h3>Заявки и допуск</h3>
          <p>
            Проверка заявок и исправлений выполняется в разделе заявок.
            Количество подтверждённых участников здесь пока не загружено.
          </p>
          <NavLink className={styles.link} to={`${base}/registrations`}>
            Открыть заявки
          </NavLink>
        </li>

        <li>
          <h3>Стартовые протоколы и расписание</h3>

          {snapshot.kind === 'loading' ? (
            <p role="status">Проверяем сохранённую программу…</p>
          ) : null}

          {snapshot.kind === 'missing' ? (
            <p>Общая программа заплывов ещё не сохранена.</p>
          ) : null}

          {snapshot.kind === 'saved' ? (
            <p>
              Есть снимок: ревизия {snapshot.revision},
              заплывов {snapshot.heats}. Его актуальность после отдельных
              изменений заплывов автоматически не подтверждена.
            </p>
          ) : null}

          {snapshot.kind === 'error' ? (
            <p role="status">{snapshot.message}</p>
          ) : null}

          {supported.length === 0 ? (
            <p>
              Для новой генерации нужны опубликованные индивидуальные
              дисциплины формата HEAT.
            </p>
          ) : null}

          <NavLink className={styles.link} to={`${base}/session-protocol`}>
            Открыть программу
          </NavLink>
        </li>

        <li>
          <h3>Проведение и результаты</h3>
          <p>
            Ввод результатов открывается отдельно для каждой дисциплины.
            Готовность результатов здесь пока не проверяется.
          </p>
          {stageLink('#event-disciplines', 'Выбрать дисциплину')}
        </li>

        <li>
          <h3>Зачёт и подтверждение итогов</h3>
          <p>
            Проверьте итоговую таблицу каждой дисциплины и состояние
            её подтверждения.
          </p>
          {stageLink('#event-disciplines', 'Открыть список дисциплин')}
        </li>
      </ol>
    </section>
  )
}