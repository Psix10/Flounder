import { buildSessionDisciplineLabels } from './sessionDisciplineLabels'
import { useEffect, useRef, useState, type FormEvent } from 'react'

import type { EventDiscipline } from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  generateSessionProtocol,
  type SessionProtocol,
} from '../../api/session-protocol.api'

import { SessionProtocolRuleSelect } from './SessionProtocolRuleSelect'

import styles from './SessionProtocolForm.module.css'

type Props = {
  eventId: string
  accessToken: string
  disciplines: EventDiscipline[]
  hasSnapshot: boolean
  onGenerated: (protocol: SessionProtocol) => void
}

type DisciplineRow = {
  id: string
  selected: boolean
  version: string
  duration: string
  basis: string
}

type BreakRow = {
  id: string
  start: string
  end: string
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function seconds(value: string, label: string, positive = false): number {
  if (!value.trim()) {
    throw new Error(`Заполните поле «${label}».`)
  }

  const number = Number(value)

  if (
    !Number.isSafeInteger(number) ||
    number < 0 ||
    (positive && number === 0)
  ) {
    throw new Error(
      `Поле «${label}»: требуется ${
        positive ? 'положительное' : 'неотрицательное'
      } целое количество секунд.`,
    )
  }

  return number
}

function timestamp(value: string, label: string): number {
  const result = new Date(value).getTime()

  if (!value || !Number.isFinite(result)) {
    throw new Error(`Укажите корректные дату и время: ${label}.`)
  }

  return result
}

function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к генерации либо выбранной версии правил.'
    }

    if (error.code === 'protocol.already_generated') {
      return 'У выбранных дисциплин уже есть заплывы. Для замены явно разрешите регенерацию.'
    }

    if (error.code === 'protocol.rule_version_required') {
      return 'Не найдена версия правил. Привяжите её к регламенту либо укажите UUID опубликованной версии.'
    }

    if (error.code === 'protocol.session_window_exceeded') {
      return 'Программа не помещается в заданное окно с выбранным порядком и временными параметрами. Предыдущая программа не заменена.'
    }

    return error.message || 'Не удалось сгенерировать программу.'
  }

  return error instanceof Error
    ? error.message
    : 'Не удалось сгенерировать программу.'
}

export function SessionProtocolForm({
  eventId,
  accessToken,
  disciplines,
  hasSnapshot,
  onGenerated,
}: Props) {
  const [sessionId] = useState(() => crypto.randomUUID())

  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [rest, setRest] = useState('')
  const [changeover, setChangeover] = useState('')
  const [source, setSource] = useState('')
  const [breaks, setBreaks] = useState<BreakRow[]>([])

  const [rows, setRows] = useState<DisciplineRow[]>(() =>
    disciplines
      .filter(
        (discipline) =>
          discipline.competitionFormat === 'INDIVIDUAL' &&
          discipline.unitType === 'HEAT' &&
          discipline.status?.toUpperCase() === 'PUBLISHED',
      )
      .map((discipline) => ({
        id: discipline.id,
        selected: false,
        version: '',
        duration: '',
        basis: '',
      })),
  )

  const [regenerate, setRegenerate] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const locked = useRef(false)
  const mounted = useRef(false)

  useEffect(() => {
    mounted.current = true

    return () => {
      mounted.current = false
    }
  }, [])

  const names = buildSessionDisciplineLabels(disciplines)

  function updateRow(id: string, patch: Partial<DisciplineRow>) {
    setRows((current) =>
      current.map((row) => (row.id === id ? { ...row, ...patch } : row)),
    )
  }

  function moveRow(index: number, direction: -1 | 1) {
    setRows((current) => {
      const target = index + direction

      if (target < 0 || target >= current.length) {
        return current
      }

      const next = [...current]
      const moved = next.splice(index, 1)[0]

      if (!moved) {
        return current
      }

      next.splice(target, 0, moved)
      return next
    })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (locked.current) {
      return
    }

    setError(null)
    setMessage(null)

    try {
      const selected = rows.filter((row) => row.selected)

      if (selected.length === 0) {
        throw new Error('Выберите хотя бы одну дисциплину.')
      }

      if (!confirmed) {
        throw new Error('Подтвердите плановые параметры и границы расчёта.')
      }

      if (hasSnapshot && !regenerate) {
        throw new Error(
          'У события уже есть снимок программы. Для изменения явно разрешите регенерацию.',
        )
      }

      const startMs = timestamp(start, 'начало сессии')
      const endMs = timestamp(end, 'окончание сессии')

      if (startMs >= endMs) {
        throw new Error('Окончание сессии должно быть позже начала.')
      }

      const pauses = breaks
        .map((pause) => ({
          start: timestamp(pause.start, 'начало перерыва'),
          end: timestamp(pause.end, 'окончание перерыва'),
        }))
        .sort((first, second) => first.start - second.start)

      let previousEnd = startMs

      for (const pause of pauses) {
        if (
          pause.start >= pause.end ||
          pause.start < previousEnd ||
          pause.end > endMs
        ) {
          throw new Error(
            'Перерывы должны находиться внутри сессии, иметь положительную длительность и не пересекаться.',
          )
        }

        previousEnd = pause.end
      }

      const request = {
        regenerate,
        timingPolicy: {
          minimumRestSeconds: seconds(rest, 'Минимальный отдых'),
          changeoverSeconds: seconds(changeover, 'Смена заплывов'),
          sourceDescription: source.trim(),
        },
        sessions: [
          {
            id: sessionId,
            start: new Date(startMs).toISOString(),
            end: new Date(endMs).toISOString(),
            breaks: pauses.map((pause) => ({
              start: new Date(pause.start).toISOString(),
              end: new Date(pause.end).toISOString(),
            })),
          },
        ],
        disciplines: selected.map((row) => {
          const version = row.version.trim()

          if (version && !UUID_PATTERN.test(version)) {
            throw new Error(
              `Некорректный UUID версии правил: ${names.get(row.id) ?? row.id}.`,
            )
          }

          if (!row.basis.trim()) {
            throw new Error(
              `Укажите основание длительности: ${names.get(row.id) ?? row.id}.`,
            )
          }

          return {
            disciplineId: row.id,
            ruleSetVersionId: version || null,
            sessionId,
            heatDurationSeconds: seconds(
              row.duration,
              `Длительность заплыва: ${names.get(row.id) ?? row.id}`,
              true,
            ),
            durationBasis: row.basis.trim(),
          }
        }),
      }

      if (!request.timingPolicy.sourceDescription) {
        throw new Error('Укажите источник временных параметров.')
      }

      if (
        !window.confirm(
          regenerate
            ? 'Заменить черновые протоколы выбранных дисциплин и сохранить новую ревизию программы?'
            : 'Создать черновые протоколы выбранных дисциплин и сохранить расписание?',
        )
      ) {
        return
      }

      locked.current = true
      setBusy(true)

      const result = await generateSessionProtocol(
        eventId,
        request,
        accessToken,
      )

      if (!mounted.current) {
        return
      }

      onGenerated(result)
      setMessage(`Программа сохранена. Ревизия ${result.revisionNo}.`)
      setRegenerate(false)
      setConfirmed(false)
    } catch (failure) {
      if (mounted.current) {
        setError(failureMessage(failure))
      }
    } finally {
      locked.current = false

      if (mounted.current) {
        setBusy(false)
      }
    }
  }

  return (
    <section className={styles.card}>
      <h2>Генерация программы</h2>

      <p className={styles.notice}>
        Расчёт для одной сессии и одного бассейна. Дисциплины выполняются
        в указанном ниже порядке. Конфликты с заплывами вне выбранного
        набора не проверяются. Плановые длительности не являются
        результатами участников или автоматически установленными нормами.
      </p>

      <p>
        Дата и время вводятся в часовом поясе браузера:{' '}
        {Intl.DateTimeFormat().resolvedOptions().timeZone}.
        Сохранённый снимок не восстанавливает параметры этой формы.
      </p>

      <form onSubmit={(event) => void submit(event)}>
        <fieldset className={styles.fields} disabled={busy}>
          <legend>Параметры сессии</legend>

          <div className={styles.grid}>
            <label>
              Начало сессии
              <input
                type="datetime-local"
                required
                value={start}
                onChange={(event) => setStart(event.target.value)}
              />
            </label>

            <label>
              Окончание сессии
              <input
                type="datetime-local"
                required
                value={end}
                onChange={(event) => setEnd(event.target.value)}
              />
            </label>

            <label>
              Минимальный отдых, секунд
              <input
                type="number"
                required
                min="0"
                step="1"
                value={rest}
                onChange={(event) => setRest(event.target.value)}
              />
            </label>

            <label>
              Смена заплывов, секунд
              <input
                type="number"
                required
                min="0"
                step="1"
                value={changeover}
                onChange={(event) => setChangeover(event.target.value)}
              />
            </label>
          </div>

          <p>
            Отдых отсчитывается от планового окончания предыдущего
            заплыва до начала следующего выступления того же участника.
          </p>

          <label>
            Источник временных параметров
            <textarea
              required
              maxLength={2000}
              rows={3}
              value={source}
              onChange={(event) => setSource(event.target.value)}
              placeholder="Документ либо явно подтверждённое решение организатора"
            />
          </label>

          <h3>Перерывы</h3>

          {breaks.length === 0 ? <p>Перерывы не заданы.</p> : null}

          {breaks.map((pause, index) => (
            <div className={styles.row} key={pause.id}>
              <label>
                Начало перерыва {index + 1}
                <input
                  type="datetime-local"
                  required
                  value={pause.start}
                  onChange={(event) =>
                    setBreaks((current) =>
                      current.map((item) =>
                        item.id === pause.id
                          ? { ...item, start: event.target.value }
                          : item,
                      ),
                    )
                  }
                />
              </label>

              <label>
                Окончание перерыва {index + 1}
                <input
                  type="datetime-local"
                  required
                  value={pause.end}
                  onChange={(event) =>
                    setBreaks((current) =>
                      current.map((item) =>
                        item.id === pause.id
                          ? { ...item, end: event.target.value }
                          : item,
                      ),
                    )
                  }
                />
              </label>

              <button
                type="button"
                onClick={() =>
                  setBreaks((current) =>
                    current.filter((item) => item.id !== pause.id),
                  )
                }
              >
                Удалить перерыв
              </button>
            </div>
          ))}

          <button
            type="button"
            disabled={breaks.length >= 100}
            onClick={() =>
              setBreaks((current) => [
                ...current,
                { id: crypto.randomUUID(), start: '', end: '' },
              ])
            }
          >
            Добавить перерыв
          </button>

          <h3>Порядок дисциплин</h3>
          <p>
            Показаны только опубликованные индивидуальные дисциплины
            формата HEAT. Выберите нужные и расположите их сверху вниз.
          </p>

          {rows.length === 0 ? (
            <p>Нет подходящих опубликованных дисциплин.</p>
          ) : null}

          {rows.map((row, index) => (
            <section className={styles.discipline} key={row.id}>
              <label className={styles.check}>
                <input
                  type="checkbox"
                  checked={row.selected}
                  onChange={(event) =>
                    updateRow(row.id, { selected: event.target.checked })
                  }
                />
                {index + 1}. {names.get(row.id) ?? row.id}
              </label>

              <div className={styles.row}>
                <button
                  type="button"
                  disabled={index === 0}
                  onClick={() => moveRow(index, -1)}
                >
                  Выше
                </button>
                <button
                  type="button"
                  disabled={index === rows.length - 1}
                  onClick={() => moveRow(index, 1)}
                >
                  Ниже
                </button>
              </div>

              {row.selected ? (
                <>
                  <SessionProtocolRuleSelect
                    key={row.id}
                    disciplineId={row.id}
                    accessToken={accessToken}
                    value={row.version}
                    onChange={(version) =>
                      updateRow(row.id, { version })
                    }
                  />

                  <label>
                    Плановая длительность одного заплыва, секунд
                    <input
                      type="number"
                      required
                      min="1"
                      step="1"
                      value={row.duration}
                      onChange={(event) =>
                        updateRow(row.id, { duration: event.target.value })
                      }
                    />
                  </label>

                  <label>
                    Основание длительности
                    <textarea
                      required
                      maxLength={2000}
                      rows={2}
                      value={row.basis}
                      onChange={(event) =>
                        updateRow(row.id, { basis: event.target.value })
                      }
                    />
                  </label>
                </>
              ) : null}
            </section>
          ))}

          <label className={styles.check}>
            <input
              type="checkbox"
              checked={regenerate}
              onChange={(event) => setRegenerate(event.target.checked)}
            />
            Разрешаю заменить существующие черновые протоколы выбранных
            дисциплин.
          </label>

          <label className={styles.check}>
            <input
              type="checkbox"
              required
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            Подтверждаю временные параметры и понимаю, что расчёт
            ограничен выбранным набором дисциплин.
          </label>

          <button type="submit" disabled={rows.length === 0}>
            {busy ? 'Формируем программу…' : 'Сформировать программу'}
          </button>
        </fieldset>
      </form>

      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {message ? <p role="status">{message}</p> : null}
    </section>
  )
}