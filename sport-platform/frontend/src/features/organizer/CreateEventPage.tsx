import { useEffect, useRef, useState, type FormEvent } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router'

import { createEvent, type CreateEventRequest } from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  getOrganizations,
  type OrganizationResponse,
} from '../../api/organizations.api'
import {
  getRegulationTemplates,
  getRegulationVersions,
  type RegulationTemplateResponse,
  type RegulationVersionResponse,
} from '../../api/regulations.api'
import { getSports, type SportResponse } from '../../api/sports.api'
import { getVenues, type VenueResponse } from '../../api/venues.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { CreateEventVenueForm } from './CreateEventVenueForm'
import {
  clearEventDraft,
  getEventDraftOwner,
  readEventDraft,
  saveEventDraft,
  type EventDraft,
} from './event-draft'
import styles from './CreateEventPage.module.css'

type FormState = EventDraft
type FormErrors = Partial<Record<keyof FormState, string>>

type CreateEventLocationState = {
  createdRegulationVersionId?: string
}

type TextFieldName =
  | 'title'
  | 'description'
  | 'registrationOpenAt'
  | 'registrationCloseAt'
  | 'eventStartAt'
  | 'eventEndAt'

type TextFieldConfig = {
  key: TextFieldName
  label: string
  type?: 'text' | 'datetime-local'
  required?: boolean
  maxLength?: number
  placeholder?: string
  minField?: TextFieldName
  multiline?: boolean
}

const initialFormState: FormState = {
  title: '',
  description: '',
  registrationOpenAt: '',
  registrationCloseAt: '',
  eventStartAt: '',
  eventEndAt: '',
  organizationId: '',
  venueId: '',
  sportId: '',
  regulationVersionId: '',
}

const mainFields: TextFieldConfig[] = [
  {
    key: 'title',
    label: 'Название',
    required: true,
    maxLength: 255,
    placeholder: 'Кубок Москвы по плаванию',
  },
  {
    key: 'description',
    label: 'Описание',
    multiline: true,
    placeholder: 'Описание события, формат участия, ключевая информация.',
  },
]

const dateFields: TextFieldConfig[] = [
  {
    key: 'registrationOpenAt',
    label: 'Открытие регистрации',
    type: 'datetime-local',
    required: true,
  },
  {
    key: 'registrationCloseAt',
    label: 'Закрытие регистрации',
    type: 'datetime-local',
    required: true,
    minField: 'registrationOpenAt',
  },
  {
    key: 'eventStartAt',
    label: 'Начало события',
    type: 'datetime-local',
    required: true,
    minField: 'registrationCloseAt',
  },
  {
    key: 'eventEndAt',
    label: 'Окончание события',
    type: 'datetime-local',
    required: true,
    minField: 'eventStartAt',
  },
]

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в систему повторно.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на выполнение операции.'
    }

    return error.message || 'Не удалось выполнить запрос.'
  }

  return error instanceof Error
    ? error.message
    : 'Не удалось выполнить запрос.'
}

function parseTime(value: string): number | null {
  if (!value.trim()) {
    return null
  }

  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? null : timestamp
}

function isPublishedRegulation(status: string): boolean {
  return status.trim().toLowerCase() === 'published'
}

function formatRegulationVersionLabel(
  version: RegulationVersionResponse,
): string {
  const date = version.effectiveFrom
    ? new Date(version.effectiveFrom)
    : null

  const effectiveFrom =
    date && !Number.isNaN(date.getTime())
      ? new Intl.DateTimeFormat('ru-RU', {
          dateStyle: 'medium',
        }).format(date)
      : 'дата не указана'

  return `Версия ${version.versionNo} · ${version.status} · с ${effectiveFrom}`
}

function validateForm(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.title.trim()) {
    errors.title = 'Укажите название мероприятия.'
  }

  if (!form.organizationId) {
    errors.organizationId = 'Выберите организацию.'
  }

  if (!form.venueId) {
    errors.venueId = 'Выберите площадку.'
  }

  if (!form.sportId) {
    errors.sportId = 'Выберите вид спорта.'
  }

  if (!form.regulationVersionId) {
    errors.regulationVersionId = 'Выберите версию регламента.'
  }

  for (const field of dateFields) {
    if (!form[field.key]) {
      errors[field.key] = 'Укажите дату и время.'
    } else if (parseTime(form[field.key]) === null) {
      errors[field.key] = 'Укажите корректные дату и время.'
    }
  }

  const open = parseTime(form.registrationOpenAt)
  const close = parseTime(form.registrationCloseAt)
  const start = parseTime(form.eventStartAt)
  const end = parseTime(form.eventEndAt)

  if (open !== null && close !== null && close < open) {
    errors.registrationCloseAt =
      'Закрытие регистрации не может быть раньше открытия.'
  }

  if (close !== null && start !== null && start < close) {
    errors.eventStartAt =
      'Начало события не может быть раньше закрытия регистрации.'
  }

  if (start !== null && end !== null && end < start) {
    errors.eventEndAt =
      'Окончание события не может быть раньше его начала.'
  }

  return errors
}

export function CreateEventPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const draftOwner = getEventDraftOwner(accessToken)

  return (
    <CreateEventPageContent
      key={draftOwner ?? accessToken ?? 'anonymous'}
      accessToken={accessToken}
      draftOwner={draftOwner}
    />
  )
}

function CreateEventPageContent({
  accessToken,
  draftOwner,
}: {
  accessToken: string | undefined
  draftOwner: string | null
}) {
  const navigate = useNavigate()
  const location = useLocation()

  const locationState = location.state as CreateEventLocationState | null
  const createdRegulationVersionId =
    locationState?.createdRegulationVersionId

  const [form, setForm] = useState<FormState>(() =>
    readEventDraft(draftOwner) ?? { ...initialFormState },
  )
  const [errors, setErrors] = useState<FormErrors>({})
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoadingOptions, setIsLoadingOptions] = useState(true)
  const [optionsReady, setOptionsReady] = useState(false)
  const [refresh, setRefresh] = useState(0)

  const [organizations, setOrganizations] =
    useState<OrganizationResponse[]>([])
  const [venues, setVenues] = useState<VenueResponse[]>([])
  const [sports, setSports] = useState<SportResponse[]>([])
  const [templates, setTemplates] =
    useState<RegulationTemplateResponse[]>([])
  const [versions, setVersions] =
    useState<RegulationVersionResponse[]>([])

  const submitting = useRef(false)
  const mounted = useRef(false)
  const appliedRegulationVersionId = useRef<string | null>(null)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    let active = true
    setOptionsReady(false)
    setErrorMessage(null)

    if (!accessToken) {
      setIsLoadingOptions(false)
      setOrganizations([])
      setVenues([])
      setSports([])
      setTemplates([])
      setVersions([])

      return () => {
        active = false
      }
    }

    const token = accessToken

    async function loadOptions() {
      setIsLoadingOptions(true)

      try {
        const [
          loadedOrganizations,
          loadedVenues,
          loadedSports,
          loadedVersions,
          loadedTemplates,
        ] = await Promise.all([
          getOrganizations(token),
          getVenues(token),
          getSports(token),
          getRegulationVersions(token),
          getRegulationTemplates(token),
        ])

        if (!active) {
          return
        }

        const activeSports = loadedSports.filter((sport) => sport.isActive)
        const publishedVersions = loadedVersions.filter(
          (version) => isPublishedRegulation(version.status),
        )

        setOrganizations(loadedOrganizations)
        setVenues(loadedVenues)
        setSports(activeSports)
        setTemplates(loadedTemplates)
        setVersions(publishedVersions)

        let selection: {
          sportId: string
          regulationVersionId: string
        } | null = null

        if (
          createdRegulationVersionId &&
          appliedRegulationVersionId.current !== createdRegulationVersionId
        ) {
          const version = publishedVersions.find(
            (item) => item.id === createdRegulationVersionId,
          )
          const template = version
            ? loadedTemplates.find(
                (item) => item.id === version.regulationTemplateId,
              )
            : undefined

          if (
            version &&
            template &&
            activeSports.some((sport) => sport.id === template.sportId)
          ) {
            selection = {
              sportId: template.sportId,
              regulationVersionId: version.id,
            }
            appliedRegulationVersionId.current = createdRegulationVersionId
          }
        }

        const returnedSelection = selection

        setForm((current) => {
          const next = { ...current, ...(returnedSelection ?? {}) }

          const organizationId = loadedOrganizations.some(
            (item) => item.id === next.organizationId,
          )
            ? next.organizationId
            : ''

          const venueId = loadedVenues.some(
            (item) => item.id === next.venueId,
          )
            ? next.venueId
            : ''

          const sportId = activeSports.some(
            (item) => item.id === next.sportId,
          )
            ? next.sportId
            : ''

          const regulationVersionId =
            sportId &&
            publishedVersions.some(
              (version) =>
                version.id === next.regulationVersionId &&
                loadedTemplates.some(
                  (template) =>
                    template.id === version.regulationTemplateId &&
                    template.sportId === sportId,
                ),
            )
              ? next.regulationVersionId
              : ''

          return {
            ...next,
            organizationId,
            venueId,
            sportId,
            regulationVersionId,
          }
        })

        setOptionsReady(true)
      } catch (error) {
        if (active) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (active) {
          setIsLoadingOptions(false)
        }
      }
    }

    void loadOptions()

    return () => {
      active = false
    }
  }, [accessToken, createdRegulationVersionId, refresh])

  const availableVersions = versions.filter((version) =>
    templates.some(
      (template) =>
        template.id === version.regulationTemplateId &&
        template.sportId === form.sportId,
    ),
  )

  const canSubmit =
    Boolean(accessToken) &&
    optionsReady &&
    !isLoadingOptions &&
    !isSubmitting &&
    organizations.length > 0 &&
    venues.length > 0 &&
    sports.length > 0 &&
    availableVersions.length > 0

  function updateField<K extends keyof FormState>(
    field: K,
    value: FormState[K],
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === 'sportId' ? { regulationVersionId: '' } : {}),
    }))

    setErrors((current) => ({
      ...current,
      [field]: undefined,
      ...(field === 'sportId'
        ? { regulationVersionId: undefined }
        : {}),
    }))

    setErrorMessage(null)
  }

  function openRegulationEditor() {
    if (
      submitting.current ||
      isSubmitting ||
      isLoadingOptions ||
      !optionsReady
    ) {
      return
    }

    setErrorMessage(null)

    if (!draftOwner) {
      setErrorMessage(
        'Не удалось определить владельца черновика. ' +
          'Переход отменён, чтобы не потерять заполненную форму.',
      )
      return
    }

    if (!saveEventDraft(draftOwner, form)) {
      setErrorMessage(
        'Не удалось сохранить черновик в браузере. ' +
          'Переход отменён; заполненные поля остаются на странице.',
      )
      return
    }

    navigate('/organizer/regulations/create')
  }

  function resetDraft() {
    if (submitting.current || isSubmitting || isLoadingOptions) {
      return
    }

    if (
      !window.confirm(
        'Очистить поля мероприятия и удалить сохранённый черновик?',
      )
    ) {
      return
    }

    if (draftOwner && !clearEventDraft(draftOwner)) {
      setErrorMessage(
        'Не удалось удалить сохранённый черновик. Поля не очищены.',
      )
      return
    }

    setForm({ ...initialFormState })
    setErrors({})
    setErrorMessage(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitting.current || isSubmitting || isLoadingOptions) {
      return
    }

    setErrorMessage(null)

    if (!accessToken) {
      setErrorMessage('Не удалось определить активную сессию.')
      return
    }

    if (!optionsReady) {
      setErrorMessage('Сначала загрузите доступные значения.')
      return
    }

    const validationErrors = validateForm(form)

    if (
      form.organizationId &&
      !organizations.some((item) => item.id === form.organizationId)
    ) {
      validationErrors.organizationId = 'Выберите доступную организацию.'
    }

    if (
      form.venueId &&
      !venues.some((item) => item.id === form.venueId)
    ) {
      validationErrors.venueId = 'Выберите доступную площадку.'
    }

    if (
      form.sportId &&
      !sports.some((item) => item.id === form.sportId)
    ) {
      validationErrors.sportId = 'Выберите доступный вид спорта.'
    }

    if (
      form.regulationVersionId &&
      !availableVersions.some(
        (item) => item.id === form.regulationVersionId,
      )
    ) {
      validationErrors.regulationVersionId =
        'Выберите опубликованный регламент для выбранного вида спорта.'
    }

    setErrors(validationErrors)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    const payload: CreateEventRequest = {
      organizationId: form.organizationId,
      venueId: form.venueId,
      sportId: form.sportId,
      regulationVersionId: form.regulationVersionId,
      title: form.title.trim(),
      description: form.description.trim() || null,
      registrationOpenAt: new Date(form.registrationOpenAt).toISOString(),
      registrationCloseAt: new Date(form.registrationCloseAt).toISOString(),
      eventStartAt: new Date(form.eventStartAt).toISOString(),
      eventEndAt: new Date(form.eventEndAt).toISOString(),
    }

    submitting.current = true
    setIsSubmitting(true)

    try {
      const created = await createEvent(payload, accessToken)

      if (draftOwner) {
        clearEventDraft(draftOwner)
      }

      if (!mounted.current) {
        return
      }

      navigate(
        `/organizer/events/${encodeURIComponent(created.id)}`,
        { replace: true },
      )
    } catch (error) {
      if (mounted.current) {
        setErrorMessage(getErrorMessage(error))
      }
    } finally {
      submitting.current = false

      if (mounted.current) {
        setIsSubmitting(false)
      }
    }
  }

  function renderTextField(field: TextFieldConfig) {
    return (
      <label
        key={field.key}
        className={
          field.type === 'datetime-local'
            ? styles.field
            : `${styles.field} ${styles.fieldFull}`
        }
      >
        <span className={styles.label}>{field.label}</span>

        {field.multiline ? (
          <textarea
            className={styles.textarea}
            rows={5}
            value={form[field.key]}
            disabled={isSubmitting}
            placeholder={field.placeholder}
            onChange={(event) =>
              updateField(field.key, event.target.value)
            }
          />
        ) : (
          <input
            className={styles.input}
            type={field.type ?? 'text'}
            required={field.required}
            maxLength={field.maxLength}
            placeholder={field.placeholder}
            min={field.minField ? form[field.minField] || undefined : undefined}
            value={form[field.key]}
            disabled={isSubmitting}
            onChange={(event) =>
              updateField(field.key, event.target.value)
            }
          />
        )}

        {errors[field.key] ? (
          <span className={styles.fieldError}>
            {errors[field.key]}
          </span>
        ) : null}
      </label>
    )
  }

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/organizer/events">
        ← Назад к моим событиям
      </NavLink>

      <p className={styles.eyebrow}>Панель организатора</p>

      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Создание мероприятия</h1>
          <p className={styles.description}>
            Заполните обязательные поля. После создания вы сможете
            настроить дисциплины и опубликовать мероприятие.
          </p>
          <p className={styles.fieldHint}>
            Перед переходом к созданию регламента форма сохраняется
            в черновик для возвращения в этой вкладке.
          </p>
        </div>
      </div>

      {!accessToken ? (
        <p className={styles.errorMessage} role="alert">
          Для создания мероприятия{' '}
          <NavLink
            to="/login"
            state={{ from: '/organizer/events/new' }}
          >
            войдите в аккаунт
          </NavLink>
          .
        </p>
      ) : null}

      {accessToken && !isLoadingOptions && !optionsReady ? (
        <div className={styles.card}>
          <p>
            Не удалось загрузить доступные значения.
            Создание мероприятия пока недоступно.
          </p>
          <button
            className={styles.ghostButton}
            type="button"
            disabled={isSubmitting}
            onClick={() => setRefresh((current) => current + 1)}
          >
            Повторить загрузку
          </button>
        </div>
      ) : null}

      {accessToken && optionsReady && !isLoadingOptions ? (
        <CreateEventVenueForm
          key={accessToken}
          accessToken={accessToken}
          disabled={isSubmitting}
          onCreated={(venue) => {
            setVenues((current) => [
              ...current.filter((item) => item.id !== venue.id),
              venue,
            ])
            setForm((current) => ({
              ...current,
              venueId: venue.id,
            }))
            setErrors((current) => ({
              ...current,
              venueId: undefined,
            }))
          }}
        />
      ) : null}

      <form
        className={styles.form}
        onSubmit={(event) => void handleSubmit(event)}
      >
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Основные данные</h2>
          <div className={styles.grid}>
            {mainFields.map(renderTextField)}
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Даты</h2>
          <div className={styles.grid}>
            {dateFields.map(renderTextField)}
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Связанные сущности</h2>

          {isLoadingOptions ? (
            <p className={styles.description} role="status">
              Загружаем доступные значения…
            </p>
          ) : optionsReady ? (
            <div className={styles.grid}>
              <label className={styles.field}>
                <span className={styles.label}>Организация</span>
                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.organizationId}
                  onChange={(event) =>
                    updateField('organizationId', event.target.value)
                  }
                >
                  <option value="">Выберите организацию</option>
                  {organizations.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>

                {organizations.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Нет доступных организаций.{' '}
                    <NavLink to="/my/organization-requests">
                      Проверить заявку организации
                    </NavLink>
                  </p>
                ) : null}

                {errors.organizationId ? (
                  <span className={styles.fieldError}>
                    {errors.organizationId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Площадка</span>
                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.venueId}
                  onChange={(event) =>
                    updateField('venueId', event.target.value)
                  }
                >
                  <option value="">Выберите площадку</option>
                  {venues.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} · {item.city}
                    </option>
                  ))}
                </select>

                {venues.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Площадки не найдены. Создайте площадку в форме выше.
                  </p>
                ) : null}

                {errors.venueId ? (
                  <span className={styles.fieldError}>
                    {errors.venueId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Вид спорта</span>
                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.sportId}
                  onChange={(event) =>
                    updateField('sportId', event.target.value)
                  }
                >
                  <option value="">Выберите вид спорта</option>
                  {sports.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>

                {sports.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Активные виды спорта не найдены.
                  </p>
                ) : null}

                {errors.sportId ? (
                  <span className={styles.fieldError}>
                    {errors.sportId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <div className={styles.fieldHeader}>
                  <span className={styles.label}>
                    Версия регламента
                  </span>
                  {!isSubmitting ? (
                    <button
                      className={styles.inlineActionButton}
                      type="button"
                      onClick={openRegulationEditor}
                    >
                      Создать регламент
                    </button>
                  ) : null}
                </div>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting || !form.sportId}
                  value={form.regulationVersionId}
                  onChange={(event) =>
                    updateField('regulationVersionId', event.target.value)
                  }
                >
                  <option value="">
                    {form.sportId
                      ? 'Выберите версию регламента'
                      : 'Сначала выберите вид спорта'}
                  </option>
                  {availableVersions.map((version) => (
                    <option key={version.id} value={version.id}>
                      {templates.find(
                        (template) =>
                          template.id === version.regulationTemplateId,
                      )?.name ?? 'Регламент'}
                      {' · '}
                      {formatRegulationVersionLabel(version)}
                    </option>
                  ))}
                </select>

                {form.sportId && availableVersions.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Для выбранного вида спорта нет опубликованных
                    регламентов. Создайте и опубликуйте версию.
                  </p>
                ) : null}

                {errors.regulationVersionId ? (
                  <span className={styles.fieldError}>
                    {errors.regulationVersionId}
                  </span>
                ) : null}
              </label>
            </div>
          ) : (
            <p className={styles.fieldHint}>
              Доступные значения пока не загружены.
            </p>
          )}
        </article>

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className={styles.actions}>
          <button
            className={styles.ghostButton}
            type="button"
            disabled={isSubmitting || isLoadingOptions}
            onClick={resetDraft}
          >
            Сбросить черновик
          </button>

          {!isSubmitting ? (
            <NavLink
              className={styles.secondaryButton}
              to="/organizer/events"
            >
              Отмена
            </NavLink>
          ) : null}

          <button
            className={styles.primaryButton}
            type="submit"
            disabled={!canSubmit}
          >
            {isSubmitting ? 'Создаём…' : 'Создать мероприятие'}
          </button>
        </div>
      </form>
    </section>
  )
}