import { useEffect, useRef, useState, type FormEvent } from 'react'
import { NavLink } from 'react-router'

import { ApiError } from '../../api/http'
import {
  createOrganizationRegistrationRequest,
  getMyPendingOrganizationRegistration,
  type OrganizationRegistrationResponse,
} from '../../api/organizations.api'
import {
  getMyOrganizationRegistrations,
  type OrganizationRegistrationReview,
} from '../../api/organization-registration-review.api'
import { useAuth } from '../../app/providers/AuthProvider'

import styles from './MyOrganizationRequestsPage.module.css'

const PAGE_SIZE = 20

const emptyForm = {
  organizationType: '',
  organizationName: '',
  legalName: '',
  inn: '',
  contactEmail: '',
  contactPhone: '',
}

type FormState = typeof emptyForm

const fields: {
  key: keyof FormState
  label: string
  required?: boolean
  maxLength: number
  type?: 'text' | 'email'
}[] = [
  {
    key: 'organizationType',
    label: 'Тип организации',
    required: true,
    maxLength: 64,
  },
  {
    key: 'organizationName',
    label: 'Название организации',
    required: true,
    maxLength: 255,
  },
  {
    key: 'legalName',
    label: 'Юридическое наименование',
    maxLength: 255,
  },
  {
    key: 'inn',
    label: 'ИНН',
    maxLength: 32,
  },
  {
    key: 'contactEmail',
    label: 'Контактная почта',
    maxLength: 255,
    type: 'email',
  },
  {
    key: 'contactPhone',
    label: 'Контактный телефон',
    maxLength: 32,
  },
]

function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в аккаунт повторно.'
    }

    if (error.code === 'organizations.registration_already_pending') {
      return 'У вас уже есть заявка на рассмотрении. Обновите состояние.'
    }

    return [
      error.message,
      ...error.violations.map(
        (violation) => `${violation.field}: ${violation.message}`,
      ),
    ].filter(Boolean).join('\n')
  }

  return 'Не удалось получить ответ сервера. Перед повторной подачей ' +
    'обновите состояние: заявка могла быть сохранена.'
}

function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'На рассмотрении'
    case 'APPROVED':
      return 'Одобрена'
    case 'REJECTED':
      return 'Отклонена'
    default:
      return status
  }
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  return Number.isNaN(date.getTime())
    ? '—'
    : new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(date)
}

function RequestContent({ accessToken }: { accessToken: string }) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [pending, setPending] =
    useState<OrganizationRegistrationResponse | null>(null)
  const [history, setHistory] =
    useState<OrganizationRegistrationReview[]>([])

  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [loading, setLoading] = useState(true)
  const [ready, setReady] = useState(false)
  const [busy, setBusy] = useState(false)

  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const saving = useRef(false)
  const mounted = useRef(false)

  useEffect(() => {
    mounted.current = true

    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    let active = true

    setLoading(true)
    setReady(false)
    setLoadError(null)

    async function load() {
      try {
        const pendingRequest = getMyPendingOrganizationRegistration(
          accessToken,
        ).catch((error: unknown) => {
          if (
            error instanceof ApiError &&
            error.code === 'organizations.pending_registration_not_found'
          ) {
            return null
          }

          throw error
        })

        const [currentPending, items] = await Promise.all([
          pendingRequest,
          getMyOrganizationRegistrations(
            accessToken,
            page,
            PAGE_SIZE,
          ),
        ])

        if (active) {
          setPending(currentPending)
          setHistory(items)
          setReady(true)
        }
      } catch (error) {
        if (active) {
          setLoadError(failureMessage(error))
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
  }, [accessToken, page, refresh])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (saving.current || !ready || pending) {
      return
    }

    setSubmitError(null)
    setMessage(null)

    if (!form.organizationType.trim() || !form.organizationName.trim()) {
      setSubmitError('Укажите тип и название организации.')
      return
    }

    saving.current = true
    setBusy(true)

    try {
      const created = await createOrganizationRegistrationRequest(
        {
          organizationType: form.organizationType.trim(),
          organizationName: form.organizationName.trim(),
          legalName: form.legalName.trim() || null,
          inn: form.inn.trim() || null,
          contactEmail: form.contactEmail.trim() || null,
          contactPhone: form.contactPhone.trim() || null,
        },
        accessToken,
      )

      if (!mounted.current) {
        return
      }

      setPending(created)
      setForm({ ...emptyForm })
      setMessage('Заявка отправлена на рассмотрение.')
      setReady(false)
      setPage(0)
      setRefresh((current) => current + 1)
    } catch (error) {
      if (mounted.current) {
        setSubmitError(failureMessage(error))
        setReady(false)
      }
    } finally {
      saving.current = false

      if (mounted.current) {
        setBusy(false)
      }
    }
  }

  return (
    <>
      <section className={styles.card}>
        <div className={styles.row}>
          <h2>Состояние заявки</h2>

          <button
            type="button"
            disabled={loading || busy}
            onClick={() => setRefresh((current) => current + 1)}
          >
            Обновить состояние
          </button>
        </div>

        {loading ? (
          <p role="status">Загружаем заявки…</p>
        ) : null}

        {loadError ? (
          <p className={styles.error} role="alert">
            {loadError}
          </p>
        ) : null}

        {!loading && ready && pending ? (
          <div className={styles.notice}>
            <p>
              Заявка «{pending.organizationName}» находится на рассмотрении.
            </p>
            <p>
              Подана: {formatDate(pending.createdAt)}.
              Пока она рассматривается, повторная подача недоступна.
            </p>
          </div>
        ) : null}

        {!loading && ready && !pending ? (
          <p>Заявок на рассмотрении нет. Можно подать новую.</p>
        ) : null}
      </section>

      <section className={styles.card}>
        <h2>Подать заявку организации</h2>
        <p>
          После одобрения создаются организация, ваше членство владельца
          и роль организатора. Подача заявки сама по себе этих прав
          не предоставляет.
        </p>

        <form onSubmit={(event) => void submit(event)}>
          <fieldset disabled={!ready || loading || busy || pending !== null}>
            <legend>Данные организации</legend>

            <div className={styles.grid}>
              {fields.map((field) => (
                <label key={field.key}>
                  {field.label}
                  <input
                    type={field.type ?? 'text'}
                    required={field.required}
                    maxLength={field.maxLength}
                    value={form[field.key]}
                    placeholder={
                      field.key === 'organizationType'
                        ? 'Например: спортивный клуб'
                        : undefined
                    }
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        [field.key]: event.target.value,
                      }))
                    }
                  />
                </label>
              ))}
            </div>

            <button type="submit">
              {busy ? 'Отправляем…' : 'Отправить заявку'}
            </button>
          </fieldset>
        </form>

        {!ready && !loading ? (
          <p>
            Перед отправкой обновите состояние, чтобы проверить,
            нет ли уже сохранённой заявки.
          </p>
        ) : null}

        {submitError ? (
          <p className={styles.error} role="alert">{submitError}</p>
        ) : null}

        {message ? <p role="status">{message}</p> : null}
      </section>

      <section className={styles.card}>
        <h2>История решений</h2>

        {!loading && ready ? (
          <>
            {history.length === 0 ? (
              <p>На этой странице истории заявок нет.</p>
            ) : (
              <div className={styles.history}>
                {history.map((request) => (
                  <article className={styles.request} key={request.id}>
                    <h3>{request.organizationName}</h3>
                    <p>{statusLabel(request.status)}</p>
                    <p>Подана: {formatDate(request.createdAt)}</p>

                    {request.reviewedAt ? (
                      <p>Рассмотрена: {formatDate(request.reviewedAt)}</p>
                    ) : null}

                    {request.status === 'REJECTED' ? (
                      <p>
                        Причина: {request.rejectionReason || 'Не указана'}
                      </p>
                    ) : null}

                    {request.status === 'APPROVED' ? (
                      <div className={styles.notice}>
                        <p>
                          Если роль организатора ещё недоступна,
                          выйдите из аккаунта и войдите заново.
                          Старый токен автоматически не обновляется.
                        </p>

                        {request.organizationId ? (
                          <NavLink to="/organizer/events/new">
                            Перейти к созданию события
                          </NavLink>
                        ) : (
                          <p>
                            В старой записи нет ссылки на организацию.
                            Обратитесь к администратору для проверки.
                          </p>
                        )}
                      </div>
                    ) : null}
                  </article>
                ))}
              </div>
            )}

            <div className={styles.row}>
              <button
                type="button"
                disabled={page === 0 || busy}
                onClick={() => setPage((current) => current - 1)}
              >
                Назад
              </button>

              <span>Страница {page + 1}</span>

              <button
                type="button"
                disabled={history.length < PAGE_SIZE || busy}
                onClick={() => setPage((current) => current + 1)}
              >
                Далее
              </button>
            </div>
          </>
        ) : null}
      </section>
    </>
  )
}

export function MyOrganizationRequestsPage() {
  const { session, isPlatformAdmin } = useAuth()
  const accessToken = session?.accessToken

  return (
    <section className={styles.page}>
      <NavLink to="/">На главную</NavLink>

      <h1>Регистрация организации</h1>

      {isPlatformAdmin ? (
        <NavLink to="/admin/organization-requests">
          Рассмотреть заявки организаций
        </NavLink>
      ) : null}

      <p>
        Подайте заявку и отслеживайте решение администратора.
        Используйте реальные сведения для рабочего режима.
      </p>

      {accessToken ? (
        <RequestContent
          key={accessToken}
          accessToken={accessToken}
        />
      ) : (
        <p role="alert">
          Для подачи заявки{' '}
          <NavLink
            to="/login"
            state={{ from: '/my/organization-requests' }}
          >
            войдите в аккаунт
          </NavLink>
          .
        </p>
      )}
    </section>
  )
}