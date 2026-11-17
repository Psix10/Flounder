import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from 'react'
import { Link, Navigate, useLocation } from 'react-router'

import { ApiError } from '../../api/http'
import {
  getMyProfile,
  updateMyProfile,
  type MyProfile,
  type ParticipantGender,
} from '../../api/profile.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from '../auth/RegisterPage.module.css'

type GenderSelection = '' | ParticipantGender

function toGenderSelection(value: string | null): GenderSelection {
  return value === 'MALE' || value === 'FEMALE' ? value : ''
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Выйдите из аккаунта и войдите снова.'
    }

    if (error.status === 403) {
      return 'Нет доступа к профилю.'
    }

    if (error.status === 400) {
      return [
        error.message || 'Проверьте выбранное значение.',
        ...error.violations.map(
          (violation) => `${violation.field}: ${violation.message}`,
        ),
      ]
        .filter(Boolean)
        .join('\n')
    }

    return error.message || 'Не удалось выполнить запрос.'
  }

  return 'Не удалось получить ответ сервера. Проверьте соединение.'
}

function formatBirthDate(value: string): string {
  const parts = value.split('-')

  return parts.length === 3
    ? `${parts[2]}.${parts[1]}.${parts[0]}`
    : value
}

export function MyProfilePage() {
  const { isAuthenticated, session } = useAuth()
  const location = useLocation()
  const accessToken = session?.accessToken

  const [profile, setProfile] = useState<MyProfile | null>(null)
  const [gender, setGender] = useState<GenderSelection>('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const saving = useRef(false)
  const saveController = useRef<AbortController | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    saveController.current?.abort()
    saveController.current = null
    saving.current = false

    setProfile(null)
    setGender('')
    setIsSaving(false)
    setErrorMessage(null)
    setSuccessMessage(null)

    if (!accessToken) {
      setIsLoading(false)
      return () => controller.abort()
    }

    const token = accessToken
    setIsLoading(true)

    async function load() {
      try {
        const response = await getMyProfile(token, controller.signal)

        if (!controller.signal.aborted) {
          setProfile(response)
          setGender(toGenderSelection(response.gender))
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void load()

    return () => {
      controller.abort()
      saveController.current?.abort()
    }
  }, [accessToken, reloadKey])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!accessToken || !profile || saving.current) {
      return
    }

    setErrorMessage(null)
    setSuccessMessage(null)

    if (gender !== 'MALE' && gender !== 'FEMALE') {
      setErrorMessage('Выберите пол участника.')
      return
    }

    const controller = new AbortController()
    saveController.current = controller
    saving.current = true
    setIsSaving(true)

    try {
      const updated = await updateMyProfile(
        accessToken,
        gender,
        controller.signal,
      )

      if (!controller.signal.aborted) {
        setProfile(updated)
        setGender(toGenderSelection(updated.gender))
        setSuccessMessage(
          'Пол сохранён в профиле. Ранее поданные заявки автоматически не изменены.',
        )
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setErrorMessage(
          `${getErrorMessage(error)} ` +
          'Если соединение прервалось, перед повторным сохранением ' +
          'обновите страницу и проверьте данные.',
        )
      }
    } finally {
      if (saveController.current === controller) {
        saveController.current = null
        saving.current = false

        if (!controller.signal.aborted) {
          setIsSaving(false)
        }
      }
    }
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        replace
        to="/login"
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  const hasInvalidStoredGender =
    profile !== null &&
    profile.gender !== null &&
    profile.gender !== '' &&
    toGenderSelection(profile.gender) === ''

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/">
        ← К событиям
      </Link>

      <div className={styles.card}>
        <p className={styles.eyebrow}>Личный кабинет</p>
        <h1 className={styles.title}>Мой профиль</h1>

        <p className={styles.description}>
          Здесь можно проверить данные профиля и указать пол участника
          для категорий соревнований. Остальные поля пока доступны
          только для просмотра.
        </p>

        {!accessToken ? (
          <p className={styles.errorMessage} role="alert">
            Токен авторизации отсутствует. Выйдите из аккаунта
            и войдите снова.
          </p>
        ) : null}

        {accessToken && isLoading ? (
          <p role="status">Загружаем профиль…</p>
        ) : null}

        {errorMessage ? (
          <p
            className={styles.errorMessage}
            role="alert"
            style={{ whiteSpace: 'pre-line' }}
          >
            {errorMessage}
          </p>
        ) : null}

        {accessToken && !isLoading && !profile ? (
          <button
            className={styles.submitButton}
            type="button"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            Повторить загрузку
          </button>
        ) : null}

        {accessToken && !isLoading && profile ? (
          <>
            <dl>
              <dt>ФИО</dt>
              <dd>
                {[
                  profile.lastName,
                  profile.firstName,
                  profile.middleName,
                ]
                  .filter(Boolean)
                  .join(' ')}
              </dd>

              <dt>Дата рождения</dt>
              <dd>{formatBirthDate(profile.birthDate)}</dd>

              <dt>Город</dt>
              <dd>{profile.city || 'Не указан'}</dd>

              <dt>Страна — код</dt>
              <dd>{profile.countryCode || 'Не указан'}</dd>

              <dt>Клуб</dt>
              <dd>{profile.clubName || 'Не указан'}</dd>
            </dl>

            {hasInvalidStoredGender ? (
              <p role="status">
                В профиле сохранено неподдерживаемое значение пола.
                Выберите корректное значение и сохраните его.
              </p>
            ) : null}

            <form
              className={styles.form}
              onSubmit={(event) => void handleSubmit(event)}
            >
              <fieldset
                className={styles.fieldset}
                disabled={isSaving}
              >
                <legend className={styles.legend}>
                  Категории соревнований
                </legend>

                <label className={styles.field}>
                  <span>Пол участника</span>
                  <select
                    name="gender"
                    required
                    value={gender}
                    onChange={(event) => {
                      const value = event.target.value

                      if (
                        value === '' ||
                        value === 'MALE' ||
                        value === 'FEMALE'
                      ) {
                        setGender(value)
                        setErrorMessage(null)
                        setSuccessMessage(null)
                      }
                    }}
                  >
                    <option value="">Выберите значение</option>
                    <option value="MALE">Мужской</option>
                    <option value="FEMALE">Женский</option>
                  </select>
                </label>

                <p>
                  Укажите значение самостоятельно. Оно используется
                  при проверке категорий, предусмотренных регламентом.
                </p>
              </fieldset>

              <p>
                Изменение профиля не обновляет снимки ранее поданных
                заявок. Исправления в таких заявках выполняются отдельно.
              </p>

              {successMessage ? (
                <p role="status">{successMessage}</p>
              ) : null}

              <button
                className={styles.submitButton}
                disabled={isSaving || gender === ''}
                type="submit"
              >
                {isSaving ? 'Сохраняем…' : 'Сохранить пол'}
              </button>
            </form>
          </>
        ) : null}
      </div>
    </section>
  )
}