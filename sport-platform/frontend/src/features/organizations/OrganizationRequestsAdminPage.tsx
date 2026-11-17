import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router'

import { ApiError } from '../../api/http'
import {
  approveOrganizationRegistration,
  getOrganizationRegistrations,
  rejectOrganizationRegistration,
  type OrganizationRegistrationReview,
  type OrganizationReviewStatus,
} from '../../api/organization-registration-review.api'
import { useAuth } from '../../app/providers/AuthProvider'

import styles from './MyOrganizationRequestsPage.module.css'

const PAGE_SIZE = 20

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

function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа. Требуется активная учётная запись администратора.'
    }

    if (error.status === 409) {
      return error.message ||
        'Состояние заявки изменилось. Обновите список.'
    }

    return [
      error.message,
      ...error.violations.map(
        (violation) => `${violation.field}: ${violation.message}`,
      ),
    ].filter(Boolean).join('\n')
  }

  return 'Не удалось получить ответ сервера. Решение могло сохраниться. ' +
    'Обновите список перед повторным действием.'
}

function AdminContent({ accessToken }: { accessToken: string }) {
  const [filter, setFilter] =
    useState<OrganizationReviewStatus>('PENDING')
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)

  const [requests, setRequests] =
    useState<OrganizationRegistrationReview[]>([])
  const [reasons, setReasons] = useState<Record<string, string>>({})

  const [loading, setLoading] = useState(true)
  const [ready, setReady] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

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

  useEffect(() => {
    let active = true

    setLoading(true)
    setReady(false)
    setError(null)

    async function load() {
      try {
        const items = await getOrganizationRegistrations(
          accessToken,
          filter,
          page,
          PAGE_SIZE,
        )

        if (active) {
          setRequests(items)
          setReady(true)
        }
      } catch (failure) {
        if (active) {
          setError(failureMessage(failure))
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
  }, [accessToken, filter, page, refresh])

  async function review(
    request: OrganizationRegistrationReview,
    action: 'approve' | 'reject',
  ) {
    if (locked.current || !ready || request.status !== 'PENDING') {
      return
    }

    setError(null)
    setMessage(null)

    const reason = (reasons[request.id] ?? '').trim()

    if (action === 'reject' && !reason) {
      setError('Перед отклонением укажите причину для заявителя.')
      return
    }

    const question = action === 'approve'
      ? `Одобрить заявку «${request.organizationName}»? ` +
        'Будут созданы организация, членство владельца и роль организатора.'
      : `Отклонить заявку «${request.organizationName}»?\n\nПричина: ${reason}`

    if (!window.confirm(question)) {
      return
    }

    locked.current = true
    setBusyId(request.id)

    try {
      const result = action === 'approve'
        ? await approveOrganizationRegistration(request.id, accessToken)
        : await rejectOrganizationRegistration(
            request.id,
            reason,
            accessToken,
          )

      if (!mounted.current) {
        return
      }

      setMessage(
        result.status === 'APPROVED'
          ? `Заявка «${result.organizationName}» одобрена. ` +
            'Заявителю нужно обновить авторизацию для получения новой роли.'
          : `Заявка «${result.organizationName}» отклонена. ` +
            'Причина доступна заявителю в истории.',
      )

      setReasons((current) => {
        const next = { ...current }
        delete next[request.id]
        return next
      })

      setReady(false)
      setRefresh((current) => current + 1)
    } catch (failure) {
      if (mounted.current) {
        setError(failureMessage(failure))
        setReady(false)
      }
    } finally {
      locked.current = false

      if (mounted.current) {
        setBusyId(null)
      }
    }
  }

  return (
    <>
      <section className={styles.card}>
        <div className={styles.row}>
          <h2>Список заявок</h2>

          <button
            type="button"
            disabled={loading || busyId !== null}
            onClick={() => setRefresh((current) => current + 1)}
          >
            Обновить список
          </button>
        </div>

        <label>
          Состояние заявок{' '}
          <select
            value={filter}
            disabled={loading || busyId !== null}
            onChange={(event) => {
              setFilter(event.target.value as OrganizationReviewStatus)
              setPage(0)
              setReady(false)
              setMessage(null)
            }}
            style={{ padding: '0.75rem', font: 'inherit' }}
          >
            <option value="PENDING">На рассмотрении</option>
            <option value="APPROVED">Одобренные</option>
            <option value="REJECTED">Отклонённые</option>
          </select>
        </label>

        {loading ? <p role="status">Загружаем заявки…</p> : null}

        {error ? (
          <p className={styles.error} role="alert">{error}</p>
        ) : null}

        {message ? (
          <p className={styles.notice} role="status">{message}</p>
        ) : null}

        {!loading && !ready ? (
          <p>
            Состояние списка не подтверждено.
            Обновите его перед рассмотрением заявок.
          </p>
        ) : null}
      </section>

      {!loading && ready ? (
        <section className={styles.card}>
          <h2>
            {filter === 'PENDING'
              ? 'Ожидают решения'
              : filter === 'APPROVED'
                ? 'Одобренные заявки'
                : 'Отклонённые заявки'}
          </h2>

          {requests.length === 0 ? (
            <p>На этой странице заявок нет.</p>
          ) : (
            <div className={styles.history}>
              {requests.map((request) => (
                <article className={styles.request} key={request.id}>
                  <h3>{request.organizationName}</h3>

                  <p>Тип: {request.organizationType}</p>
                  <p>Подана: {formatDate(request.createdAt)}</p>

                  <dl>
                    <dt>Юридическое наименование</dt>
                    <dd>{request.legalName || '—'}</dd>

                    <dt>ИНН</dt>
                    <dd>{request.inn || '—'}</dd>

                    <dt>Контактная почта</dt>
                    <dd>{request.contactEmail || '—'}</dd>

                    <dt>Контактный телефон</dt>
                    <dd>{request.contactPhone || '—'}</dd>
                  </dl>

                  <details>
                    <summary>Идентификаторы и аудит</summary>
                    <p>Заявка: {request.id}</p>
                    <p>Заявитель: {request.applicantUserId}</p>
                    <p>Рассмотрена: {formatDate(request.reviewedAt)}</p>
                    <p>
                      Администратор: {request.reviewedByUserId || '—'}
                    </p>
                    <p>
                      Организация: {request.organizationId || '—'}
                    </p>
                  </details>

                  {request.status === 'REJECTED' ? (
                    <p>Причина: {request.rejectionReason || 'Не указана'}</p>
                  ) : null}

                  {request.status === 'PENDING' ? (
                    <fieldset disabled={busyId !== null}>
                      <legend>Решение по заявке</legend>

                      <label style={{ display: 'grid', gap: '0.5rem' }}>
                        Причина отклонения
                        <textarea
                          rows={3}
                          maxLength={2000}
                          value={reasons[request.id] ?? ''}
                          onChange={(event) =>
                            setReasons((current) => ({
                              ...current,
                              [request.id]: event.target.value,
                            }))
                          }
                          style={{
                            boxSizing: 'border-box',
                            width: '100%',
                            padding: '0.75rem',
                            font: 'inherit',
                          }}
                        />
                      </label>

                      <div className={styles.row}>
                        <button
                          type="button"
                          onClick={() => void review(request, 'approve')}
                        >
                          {busyId === request.id
                            ? 'Сохраняем решение…'
                            : 'Одобрить'}
                        </button>

                        <button
                          type="button"
                          disabled={!(reasons[request.id] ?? '').trim()}
                          onClick={() => void review(request, 'reject')}
                        >
                          Отклонить с причиной
                        </button>
                      </div>
                    </fieldset>
                  ) : null}
                </article>
              ))}
            </div>
          )}

          <div className={styles.row}>
            <button
              type="button"
              disabled={page === 0 || busyId !== null}
              onClick={() => {
                setReady(false)
                setPage((current) => current - 1)
              }}
            >
              Назад
            </button>

            <span>Страница {page + 1}</span>

            <button
              type="button"
              disabled={
                requests.length < PAGE_SIZE || busyId !== null
              }
              onClick={() => {
                setReady(false)
                setPage((current) => current + 1)
              }}
            >
              Далее
            </button>
          </div>
        </section>
      ) : null}
    </>
  )
}

export function OrganizationRequestsAdminPage() {
  const { session, isPlatformAdmin } = useAuth()
  const accessToken = session?.accessToken

  return (
    <section className={styles.page}>
      <NavLink to="/my/organization-requests">
        К моим заявкам
      </NavLink>

      <h1>Рассмотрение заявок организаций</h1>

      <p>
        Одобрение создаёт организацию, назначает заявителя владельцем
        и выдаёт роль организатора одной серверной операцией.
        Изменение уже принятого решения здесь не предусмотрено.
      </p>

      {accessToken && isPlatformAdmin ? (
        <AdminContent key={accessToken} accessToken={accessToken} />
      ) : (
        <p className={styles.error} role="alert">
          Требуется учётная запись администратора платформы.
        </p>
      )}
    </section>
  )
}