// DISCIPLINE_METADATA_ADMISSION_V1
import { SwimmingDisciplineFields } from './SwimmingDisciplineFields'
import { emptySwimmingFields, buildSwimmingSettings } from '../../lib/swimming-discipline-settings.mjs'
import { useEffect, useRef, useState, type FormEvent } from 'react'

import {
  createEventDiscipline,
  type EventDiscipline,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  getDisciplineTemplates,
  type DisciplineTemplateResponse,
} from '../../api/sports.api'
import styles from './CreateEventDisciplineForm.module.css'

type Props = {
  eventId: string
  sportId: string
  accessToken: string
  disabled: boolean
  existingCodes: string[]
  onCreated: (discipline: EventDiscipline) => void
  onBusyChange: (busy: boolean) => void
}

function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к настройке дисциплин этого мероприятия.'
    }

    return error.message || fallback
  }

  return fallback
}

export function CreateEventDisciplineForm({
  eventId,
  sportId,
  accessToken,
  disabled,
  existingCodes,
  onCreated,
  onBusyChange,
}: Props) {
  const [templates, setTemplates] = useState<DisciplineTemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  const [templateId, setTemplateId] = useState('')
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [participantLimit, setParticipantLimit] = useState('')
  const [entryFee, setEntryFee] = useState('0')
  const [swimmingFields, setSwimmingFields] = useState(emptySwimmingFields)

  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const submitting = useRef(false)

  useEffect(() => {
    let active = true

    async function load() {
      setLoading(true)
      setLoadError(null)

      try {
        const items = await getDisciplineTemplates(accessToken)

        if (active) {
          setTemplates(
            items.filter((item) => item.sportId === sportId),
          )
        }
      } catch (error) {
        if (active) {
          setLoadError(
            errorText(error, 'Не удалось загрузить шаблоны дисциплин.'),
          )
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [accessToken, eventId, sportId, attempt])

  function selectTemplate(id: string) {
    const template = templates.find((item) => item.id === id)

    setTemplateId(id)
    setSwimmingFields(emptySwimmingFields())
    setCode(template?.code ?? '')
    setName(template?.name ?? '')
    setSubmitError(null)
  }

  async function handleSubmit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()

    if (disabled || submitting.current || loading || loadError) {
      return
    }

    setSubmitError(null)

    const selectedTemplate = templates.find(
      (template) => template.id === templateId,
    )
    const resolvedCode = code.trim()
    const resolvedName = name.trim()
    const limit = participantLimit === '' ? null : Number(participantLimit)
    const fee = Number(entryFee)

    if (!selectedTemplate || !resolvedCode || !resolvedName) {
      setSubmitError('Выберите шаблон, укажите код и название.')
      return
    }

    if (existingCodes.includes(resolvedCode)) {
      setSubmitError(
        'Дисциплина с таким кодом уже есть. Укажите другой код.',
      )
      return
    }

    if (limit !== null && (!Number.isSafeInteger(limit) || limit <= 0)) {
      setSubmitError('Лимит участников должен быть целым положительным числом.')
      return
    }

    if (entryFee.trim() === '' || !Number.isFinite(fee) || fee < 0) {
      setSubmitError('Укажите неотрицательную сумму взноса.')
      return
    }

    let swimmingSettings: Record<string, unknown> | undefined
    try { swimmingSettings = buildSwimmingSettings(swimmingFields) }
    catch (error) { setSubmitError(error instanceof Error ? error.message : 'Проверьте параметры плавания.'); return }

    submitting.current = true
    setSaving(true)
    onBusyChange(true)

    try {
      const discipline = await createEventDiscipline(
        eventId,
        {
          disciplineTemplateId: selectedTemplate.id,
          code: resolvedCode,
          name: resolvedName,
          participantLimit: limit,
          entryFeeAmount: fee,
          entryFeeCurrency: 'RUB',
          ...(swimmingSettings ? { settingsJson: swimmingSettings } : {}),
        },
        accessToken,
      )

      onCreated(discipline)

      setTemplateId('')
      setCode('')
      setName('')
      setParticipantLimit('')
      setEntryFee('0')
      setSwimmingFields(emptySwimmingFields())
    } catch (error) {
      setSubmitError(
        errorText(error, 'Не удалось создать дисциплину.'),
      )
    } finally {
      submitting.current = false
      setSaving(false)
      onBusyChange(false)
    }
  }

  if (loading) {
    return <p role="status">Загружаем шаблоны дисциплин…</p>
  }

  if (loadError) {
    return (
      <div className={styles.notice}>
        <p role="alert">{loadError}</p>
        <button
          type="button"
          className={styles.button}
          onClick={() => setAttempt((value) => value + 1)}
        >
          Повторить загрузку
        </button>
      </div>
    )
  }

  if (templates.length === 0) {
    return (
      <p className={styles.notice}>
        Для выбранного вида спорта нет шаблонов дисциплин.
        Администратору нужно добавить их в справочник.
      </p>
    )
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <h3 className={styles.title}>Добавить дисциплину</h3>

      <p className={styles.hint}>
        Выберите шаблон. Название и код можно изменить для этого мероприятия.
        Формат наследуется от шаблона. Параметры плавания можно уточнить ниже; остальные настройки шаблона сохраняются.
      </p>

      <fieldset className={styles.fields} disabled={disabled || saving}>
        <label className={styles.field}>
          Шаблон дисциплины
          <select
            required
            value={templateId}
            onChange={(event) => selectTemplate(event.target.value)}
          >
            <option value="">Выберите шаблон</option>
            {templates.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name} — {template.code}
              </option>
            ))}
          </select>
        </label>

        <div className={styles.grid}>
          <label className={styles.field}>
            Код
            <input
              required
              maxLength={64}
              value={code}
              onChange={(event) => setCode(event.target.value)}
            />
          </label>

          <label className={styles.field}>
            Название
            <input
              required
              maxLength={255}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <label className={styles.field}>
            Лимит участников
            <input
              type="number"
              min="1"
              step="1"
              max="2147483647"
              placeholder="Без ограничения"
              value={participantLimit}
              onChange={(event) => setParticipantLimit(event.target.value)}
            />
          </label>

          <label className={styles.field}>
            Стартовый взнос, RUB
            <input
              required
              type="number"
              min="0"
              max="9999999999.99"
              step="0.01"
              value={entryFee}
              onChange={(event) => setEntryFee(event.target.value)}
            />
          </label>
        </div>

        <SwimmingDisciplineFields value={swimmingFields} onChange={setSwimmingFields} />

        <button
          type="submit"
          className={styles.button}
          disabled={!templateId}
        >
          {saving ? 'Сохраняем…' : 'Добавить дисциплину'}
        </button>
      </fieldset>

      {submitError ? (
        <p className={styles.error} role="alert">
          {submitError}
        </p>
      ) : null}
    </form>
  )
}