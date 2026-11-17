import { useEffect, useRef, useState, type FormEvent } from 'react'

import { ApiError } from '../../api/http'
import { createVenue } from '../../api/venue-creation.api'
import type { VenueResponse } from '../../api/venues.api'

import styles from './CreateEventPage.module.css'

type Props = {
  accessToken: string
  disabled?: boolean
  onCreated: (venue: VenueResponse) => void
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в аккаунт повторно.'
    }

    if (error.status === 403) {
      return 'Для создания площадки требуется роль организатора или администратора.'
    }

    return [
      error.message,
      ...error.violations.map(
        (violation) => `${violation.field}: ${violation.message}`,
      ),
    ].filter(Boolean).join('\n')
  }

  return 'Не удалось получить подтверждение создания площадки. ' +
    'Перед повтором проверьте список площадок: запрос мог быть выполнен сервером.'
}

function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || ''
  } catch {
    return ''
  }
}

export function CreateEventVenueForm({
  accessToken,
  disabled = false,
  onCreated,
}: Props) {
  const [name, setName] = useState('')
  const [countryCode, setCountryCode] = useState('')
  const [city, setCity] = useState('')
  const [address, setAddress] = useState('')
  const [timezone, setTimezone] = useState(browserTimezone)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const saving = useRef(false)
  const mounted = useRef(false)

  useEffect(() => {
    mounted.current = true

    return () => {
      mounted.current = false
    }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (disabled || saving.current) {
      return
    }

    setError(null)
    setSuccess(null)

    const resolvedName = name.trim()
    const resolvedTimezone = timezone.trim()
    const resolvedCountry = countryCode.trim().toUpperCase()

    if (!resolvedName || !resolvedTimezone) {
      setError('Укажите название площадки и часовой пояс.')
      return
    }

    if (resolvedCountry && !/^[A-Z]{2}$/.test(resolvedCountry)) {
      setError('Укажите двухбуквенный код страны, например RU, либо оставьте поле пустым.')
      return
    }

    try {
      new Intl.DateTimeFormat('ru-RU', {
        timeZone: resolvedTimezone,
      }).format(new Date())
    } catch {
      setError(
        'Часовой пояс не распознан браузером. ' +
        'Укажите название, например Europe/Moscow.',
      )
      return
    }

    saving.current = true
    setBusy(true)

    try {
      const created = await createVenue(
        {
          name: resolvedName,
          countryCode: resolvedCountry || null,
          city: city.trim() || null,
          address: address.trim() || null,
          timezone: resolvedTimezone,
        },
        accessToken,
      )

      if (!mounted.current) {
        return
      }

      onCreated(created)

      setSuccess(
        `Площадка «${created.name}» создана и выбрана в форме события.`,
      )

      setName('')
      setCountryCode('')
      setCity('')
      setAddress('')
    } catch (failure) {
      if (mounted.current) {
        setError(messageFor(failure))
      }
    } finally {
      saving.current = false

      if (mounted.current) {
        setBusy(false)
      }
    }
  }

  return (
    <section className={styles.card}>
      <details>
        <summary>Нет подходящей площадки? Создать новую</summary>

        <p className={styles.description}>
          Создайте площадку, затем продолжите заполнение события ниже.
          Уже введённые данные события останутся на странице.
        </p>

        <form onSubmit={(event) => void submit(event)}>
          <fieldset disabled={busy || disabled}>
            <legend>Новая площадка</legend>

            <div className={styles.grid}>
              <label className={styles.field}>
                <span className={styles.label}>Название</span>
                <input
                  className={styles.input}
                  required
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Бассейн спортивного комплекса"
                />
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Код страны</span>
                <input
                  className={styles.input}
                  maxLength={2}
                  value={countryCode}
                  onChange={(event) => setCountryCode(event.target.value)}
                  placeholder="RU"
                />
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Город</span>
                <input
                  className={styles.input}
                  value={city}
                  onChange={(event) => setCity(event.target.value)}
                />
              </label>

              <label className={styles.field}>
                <span className={styles.label}>Часовой пояс площадки</span>
                <input
                  className={styles.input}
                  required
                  value={timezone}
                  onChange={(event) => setTimezone(event.target.value)}
                  placeholder="Europe/Moscow"
                />
              </label>

              <label className={`${styles.field} ${styles.fieldFull}`}>
                <span className={styles.label}>Адрес</span>
                <input
                  className={styles.input}
                  value={address}
                  onChange={(event) => setAddress(event.target.value)}
                />
              </label>
            </div>

            <p className={styles.description}>
              По умолчанию предложен часовой пояс браузера.
              Проверьте, что он соответствует месту проведения.
              Эта настройка сама по себе не меняет часовой пояс
              полей дат в форме события.
            </p>

            <button
              className={styles.primaryButton}
              type="submit"
            >
              {busy ? 'Создаём площадку…' : 'Создать и выбрать площадку'}
            </button>
          </fieldset>
        </form>
      </details>

      {error ? (
        <p
          className={styles.errorMessage}
          role="alert"
          style={{ whiteSpace: 'pre-line' }}
        >
          {error}
        </p>
      ) : null}

      {success ? (
        <p className={styles.successMessage} role="status">
          {success}
        </p>
      ) : null}
    </section>
  )
}