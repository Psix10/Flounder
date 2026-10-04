import { useEffect, useState, type FormEvent } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import {
  createEvent,
  type CreateEventRequest,
} from '../../api/events.api'
import {
  getOrganizations,
  type OrganizationResponse,
} from '../../api/organizations.api'
import {
  getVenues,
  type VenueResponse,
} from '../../api/venues.api'
import {
  getSports,
  type SportResponse,
} from '../../api/sports.api'
import {
  getRegulationVersions,
  type RegulationVersionResponse,
} from '../../api/regulations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './CreateEventPage.module.css'

type FormState = {
  title: string
  description: string
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
}

type FormErrors = Partial<Record<keyof FormState, string>>

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

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на создание мероприятий.'
    }

    if (error.status === 400) {
      return error.message || 'Проверьте корректность заполненных данных.'
    }

    return error.message
  }

  return 'Не удалось создать мероприятие.'
}

function toIsoOrUndefined(value: string) {
  if (!value.trim()) {
    return undefined
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return undefined
  }

  return date.toISOString()
}

function formatRegulationVersionLabel(
  regulationVersion: RegulationVersionResponse,
) {
  const effectiveFrom = regulationVersion.effectiveFrom
    ? new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'medium',
      }).format(new Date(regulationVersion.effectiveFrom))
    : 'дата не указана'

  return `Версия ${regulationVersion.versionNo} · ${regulationVersion.status} · c ${effectiveFrom}`
}

function isPublishedRegulation(status: string) {
  return status.trim().toLowerCase() === 'published'
}

function validateForm(form: FormState) {
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

  if (!form.eventStartAt) {
    errors.eventStartAt = 'Укажите дату начала события.'
  }

  if (!form.eventEndAt) {
    errors.eventEndAt = 'Укажите дату окончания события.'
  }

  const registrationOpenAt = form.registrationOpenAt
    ? new Date(form.registrationOpenAt).getTime()
    : null
  const registrationCloseAt = form.registrationCloseAt
    ? new Date(form.registrationCloseAt).getTime()
    : null
  const eventStartAt = form.eventStartAt
    ? new Date(form.eventStartAt).getTime()
    : null
  const eventEndAt = form.eventEndAt
    ? new Date(form.eventEndAt).getTime()
    : null

  if (
    registrationOpenAt !== null &&
    registrationCloseAt !== null &&
    registrationCloseAt < registrationOpenAt
  ) {
    errors.registrationCloseAt =
      'Закрытие регистрации не может быть раньше открытия.'
  }

  if (
    registrationCloseAt !== null &&
    eventStartAt !== null &&
    eventStartAt < registrationCloseAt
  ) {
    errors.eventStartAt =
      'Дата начала события не может быть раньше закрытия регистрации.'
  }

  if (
    eventStartAt !== null &&
    eventEndAt !== null &&
    eventEndAt < eventStartAt
  ) {
    errors.eventEndAt =
      'Дата окончания события не может быть раньше даты начала.'
  }

  return errors
}

export function CreateEventPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const createdRegulationVersionId =
    (location.state as { createdRegulationVersionId?: string } | null)
      ?.createdRegulationVersionId

  const [form, setForm] = useState<FormState>(initialFormState)
  const [errors, setErrors] = useState<FormErrors>({})
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoadingOptions, setIsLoadingOptions] = useState(true)

  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([])
  const [venues, setVenues] = useState<VenueResponse[]>([])
  const [sports, setSports] = useState<SportResponse[]>([])
  const [regulationVersions, setRegulationVersions] = useState<
    RegulationVersionResponse[]
  >([])

  useEffect(() => {
    if (!accessToken) {
      setIsLoadingOptions(false)
      return
    }

    let isMounted = true

    async function loadOptions() {
      setIsLoadingOptions(true)
      setErrorMessage(null)

      try {
        const [
          loadedOrganizations,
          loadedVenues,
          loadedSports,
          loadedRegulationVersions,
        ] = await Promise.all([
          getOrganizations(accessToken),
          getVenues(accessToken),
          getSports(accessToken),
          getRegulationVersions(accessToken),
        ])

        if (!isMounted) {
          return
        }

        const publishedRegulationVersions = loadedRegulationVersions.filter(
          (regulationVersion) =>
            isPublishedRegulation(regulationVersion.status),
        )

        setOrganizations(loadedOrganizations)
        setVenues(loadedVenues)
        setSports(loadedSports)
        setRegulationVersions(publishedRegulationVersions)

        if (
          createdRegulationVersionId &&
          publishedRegulationVersions.some(
            (version) => version.id === createdRegulationVersionId,
          )
        ) {
          setForm((current) => ({
            ...current,
            regulationVersionId: createdRegulationVersionId,
          }))
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(error) ||
              'Не удалось загрузить связанные сущности.',
          )
        }
      } finally {
        if (isMounted) {
          setIsLoadingOptions(false)
        }
      }
    }

    void loadOptions()

    return () => {
      isMounted = false
    }
  }, [accessToken, createdRegulationVersionId])

  function updateField<K extends keyof FormState>(
    field: K,
    value: FormState[K],
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))

    setErrors((current) => ({
      ...current,
      [field]: undefined,
    }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const validationErrors = validateForm(form)
    setErrors(validationErrors)
    setErrorMessage(null)
    setSuccessMessage(null)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    if (!accessToken) {
      setErrorMessage('Не удалось определить активную сессию.')
      return
    }

    const payload: CreateEventRequest = {
      organizationId: form.organizationId,
      venueId: form.venueId,
      sportId: form.sportId,
      regulationVersionId: form.regulationVersionId,
      title: form.title.trim(),
      description: form.description.trim() || null,
      registrationOpenAt: toIsoOrUndefined(form.registrationOpenAt),
      registrationCloseAt: toIsoOrUndefined(form.registrationCloseAt),
      eventStartAt: new Date(form.eventStartAt).toISOString(),
      eventEndAt: new Date(form.eventEndAt).toISOString(),
    }

    setIsSubmitting(true)

    try {
      const created = await createEvent(payload, accessToken)
      setSuccessMessage(`Мероприятие «${created.title}» успешно создано.`)

      setTimeout(() => {
        navigate('/organizer/events', {
          replace: true,
          state: {
            createdEventId: created.id,
          },
        })
      }, 800)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
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
            Заполните обязательные поля события. После создания вы сможете
            перейти к дальнейшей настройке дисциплин и публикации.
          </p>
        </div>
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Основные данные</h2>

          <div className={styles.grid}>
            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Название</span>
              <input
                className={styles.input}
                value={form.title}
                onChange={(event) => updateField('title', event.target.value)}
                placeholder="Кубок Москвы по плаванию"
              />
              {errors.title ? (
                <span className={styles.fieldError}>{errors.title}</span>
              ) : null}
            </label>

            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Описание</span>
              <textarea
                className={styles.textarea}
                rows={5}
                value={form.description}
                onChange={(event) =>
                  updateField('description', event.target.value)
                }
                placeholder="Описание события, формат участия, ключевая информация."
              />
            </label>
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Даты</h2>

          <div className={styles.grid}>
            <label className={styles.field}>
              <span className={styles.label}>Открытие регистрации</span>
              <input
                className={styles.input}
                type="datetime-local"
                value={form.registrationOpenAt}
                onChange={(event) =>
                  updateField('registrationOpenAt', event.target.value)
                }
              />
            </label>

            <label className={styles.field}>
              <span className={styles.label}>Закрытие регистрации</span>
              <input
                className={styles.input}
                type="datetime-local"
                value={form.registrationCloseAt}
                onChange={(event) =>
                  updateField('registrationCloseAt', event.target.value)
                }
              />
              {errors.registrationCloseAt ? (
                <span className={styles.fieldError}>
                  {errors.registrationCloseAt}
                </span>
              ) : null}
            </label>

            <label className={styles.field}>
              <span className={styles.label}>Начало события</span>
              <input
                className={styles.input}
                type="datetime-local"
                value={form.eventStartAt}
                onChange={(event) =>
                  updateField('eventStartAt', event.target.value)
                }
              />
              {errors.eventStartAt ? (
                <span className={styles.fieldError}>{errors.eventStartAt}</span>
              ) : null}
            </label>

            <label className={styles.field}>
              <span className={styles.label}>Окончание события</span>
              <input
                className={styles.input}
                type="datetime-local"
                value={form.eventEndAt}
                onChange={(event) =>
                  updateField('eventEndAt', event.target.value)
                }
              />
              {errors.eventEndAt ? (
                <span className={styles.fieldError}>{errors.eventEndAt}</span>
              ) : null}
            </label>
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Связанные сущности</h2>

          {isLoadingOptions ? (
            <p className={styles.description}>Загружаем доступные значения…</p>
          ) : (
            <div className={styles.grid}>
              <label className={styles.field}>
                <span className={styles.label}>Организация</span>
                <select
                  className={styles.input}
                  required
                  value={form.organizationId}
                  onChange={(event) =>
                    updateField('organizationId', event.target.value)
                  }
                >
                  <option value="">Выберите организацию</option>
                  {organizations.map((organization) => (
                    <option key={organization.id} value={organization.id}>
                      {organization.name}
                    </option>
                  ))}
                </select>
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
                  value={form.venueId}
                  onChange={(event) => updateField('venueId', event.target.value)}
                >
                  <option value="">Выберите площадку</option>
                  {venues.map((venue) => (
                    <option key={venue.id} value={venue.id}>
                      {venue.name} · {venue.city}
                    </option>
                  ))}
                </select>
                {errors.venueId ? (
                  <span className={styles.fieldError}>{errors.venueId}</span>
                ) : null}
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Вид спорта</span>
                <select
                  className={styles.input}
                  required
                  value={form.sportId}
                  onChange={(event) => updateField('sportId', event.target.value)}
                >
                  <option value="">Выберите вид спорта</option>
                  {sports
                    .filter((sport) => sport.isActive)
                    .map((sport) => (
                      <option key={sport.id} value={sport.id}>
                        {sport.name}
                      </option>
                    ))}
                </select>
                {errors.sportId ? (
                  <span className={styles.fieldError}>{errors.sportId}</span>
                ) : null}
              </label>

              <label className={styles.field}>
                <div className={styles.fieldHeader}>
                  <span className={styles.label}>Версия регламента</span>
                  <NavLink
                    className={styles.inlineAction}
                    to="/organizer/regulations/create"
                  >
                    Создать регламент
                  </NavLink>
                </div>

                <select
                  className={styles.input}
                  required
                  value={form.regulationVersionId}
                  onChange={(event) =>
                    updateField('regulationVersionId', event.target.value)
                  }
                >
                  <option value="">Выберите версию регламента</option>
                  {regulationVersions.map((regulationVersion) => (
                    <option
                      key={regulationVersion.id}
                      value={regulationVersion.id}
                    >
                      {formatRegulationVersionLabel(regulationVersion)}
                    </option>
                  ))}
                </select>

                {regulationVersions.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Нет опубликованных регламентов. Создайте и опубликуйте
                    версию, чтобы использовать её в мероприятии.
                  </p>
                ) : null}

                {errors.regulationVersionId ? (
                  <span className={styles.fieldError}>
                    {errors.regulationVersionId}
                  </span>
                ) : null}
              </label>
            </div>
          )}
        </article>

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}

        {successMessage ? (
          <p className={styles.successMessage} role="status">
            {successMessage}
          </p>
        ) : null}

        <div className={styles.actions}>
          <NavLink className={styles.secondaryButton} to="/organizer/events">
            Отмена
          </NavLink>

          <button
            className={styles.primaryButton}
            type="submit"
            disabled={isSubmitting || isLoadingOptions}
          >
            {isSubmitting ? 'Создаём…' : 'Создать мероприятие'}
          </button>
        </div>
      </form>
    </section>
  )
}