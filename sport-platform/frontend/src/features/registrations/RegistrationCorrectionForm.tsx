import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from 'react'
import { Link } from 'react-router'

import { ApiError } from '../../api/http'
import { getMyProfile, type MyProfile } from '../../api/profile.api'
import { resubmitRegistration } from '../../api/registrations.api'
import type { Registration } from './registration.types'
import styles from './RegistrationCorrectionForm.module.css'

type Props = {
  registration: Registration
  accessToken: string
  onUpdated: (registration: Registration) => void
}

function isSupportedGender(value: unknown): value is 'MALE' | 'FEMALE' {
  return value === 'MALE' || value === 'FEMALE'
}

function genderLabel(value: unknown): string {
  if (value === 'MALE') {
    return 'Мужской'
  }

  if (value === 'FEMALE') {
    return 'Женский'
  }

  if (value === null || value === undefined || value === '') {
    return 'Не указан'
  }

  return 'Некорректное значение'
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите снова.'
    }

    if (error.status === 403) {
      return 'Нет доступа к изменению этой заявки.'
    }

    if (error.status === 409) {
      return (
        'Исправления не приняты. Обновите страницу и проверьте статус ' +
        'заявки, данные профиля и подтверждение переноса пола. ' +
        'Также изменение блокируется при наличии платежа или протоколов.'
      )
    }

    if (error.status === 400) {
      return [
        error.message || 'Проверьте заполнение формы.',
        ...error.violations.map(
          (violation) => `${violation.field}: ${violation.message}`,
        ),
      ].join('\n')
    }

    return error.message || 'Не удалось отправить исправления.'
  }

  return (
    'Не удалось получить ответ сервера. Перед повторной отправкой ' +
    'обновите страницу и проверьте текущий статус заявки.'
  )
}

export function RegistrationCorrectionForm({
  registration,
  accessToken,
  onUpdated,
}: Props) {
  const [reply, setReply] = useState('')
  const [contactName, setContactName] = useState(
    registration.registrationMeta?.emergencyContactName ?? '',
  )
  const [contactPhone, setContactPhone] = useState(
    registration.registrationMeta?.emergencyContactPhone ?? '',
  )
  const [medicalCertificate, setMedicalCertificate] = useState(
    registration.registrationMeta?.medicalCertificate ?? false,
  )
  const [agreeToRules, setAgreeToRules] = useState(false)

  const [profile, setProfile] = useState<MyProfile | null>(null)
  const [isProfileLoading, setIsProfileLoading] = useState(true)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [profileReload, setProfileReload] = useState(0)
  const [refreshGenderFromProfile, setRefreshGenderFromProfile] =
    useState(false)

  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const submitting = useRef(false)

  useEffect(() => {
    const controller = new AbortController()

    setProfile(null)
    setProfileError(null)
    setIsProfileLoading(true)
    setRefreshGenderFromProfile(false)

    async function loadProfile() {
      try {
        const response = await getMyProfile(
          accessToken,
          controller.signal,
        )

        if (!controller.signal.aborted) {
          setProfile(response)
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setProfileError(
            error instanceof ApiError && error.status === 401
              ? 'Сессия истекла. Войдите снова.'
              : 'Не удалось загрузить профиль для сравнения данных.',
          )
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsProfileLoading(false)
        }
      }
    }

    void loadProfile()

    return () => controller.abort()
  }, [accessToken, profileReload])

  const snapshotGender = registration.participantSnapshot.gender

  const profileMatchesRegistration =
    profile !== null &&
    profile.userId === registration.participantUserId &&
    profile.id === registration.participantProfileId

  const canCopyGender =
    !isProfileLoading &&
    !profileError &&
    profileMatchesRegistration &&
    isSupportedGender(profile?.gender)

  const requiresGenderConsent =
    registration.registrationMeta?.confirmedCorrection?.reservePlace ===
      true && !isSupportedGender(snapshotGender)

  const missingRequiredConsent =
    requiresGenderConsent &&
    (!refreshGenderFromProfile || !canCopyGender)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitting.current) {
      return
    }

    setErrorMessage(null)

    const normalizedReply = reply.trim()
    const normalizedName = contactName.trim()
    const normalizedPhone = contactPhone.trim()

    if (!normalizedReply || !normalizedName || !normalizedPhone) {
      setErrorMessage('Заполните ответ и контактные данные.')
      return
    }

    if (!agreeToRules) {
      setErrorMessage('Подтвердите согласие с правилами.')
      return
    }

    if (refreshGenderFromProfile && !canCopyGender) {
      setErrorMessage(
        'Сначала загрузите профиль и проверьте корректность пола.',
      )
      return
    }

    if (missingRequiredConsent) {
      setErrorMessage(
        'В снимке заявки отсутствует корректный пол. ' +
        'Заполните профиль и явно подтвердите перенос значения.',
      )
      return
    }

    submitting.current = true
    setIsSaving(true)

    try {
      const updated = await resubmitRegistration(
        registration.id,
        {
          reply: normalizedReply,
          emergencyContactName: normalizedName,
          emergencyContactPhone: normalizedPhone,
          medicalCertificate,
          agreeToRules,
          refreshGenderFromProfile,
        },
        accessToken,
      )

      onUpdated(updated)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      submitting.current = false
      setIsSaving(false)
    }
  }

  return (
    <form
      className={styles.form}
      onSubmit={(event) => void handleSubmit(event)}
    >
      <h3 className={styles.title}>Ответить на уточнение</h3>

      <fieldset className={styles.fields} disabled={isSaving}>
        <div>
          <h4>Пол участника</h4>

          <p>
            В снимке заявки: {genderLabel(snapshotGender)}
          </p>

          {isProfileLoading ? (
            <p role="status">Загружаем текущий профиль…</p>
          ) : null}

          {profileError ? (
            <p className={styles.error} role="alert">
              {profileError}
            </p>
          ) : null}

          {!isProfileLoading && profile ? (
            <p>
              В текущем профиле: {genderLabel(profile.gender)}
            </p>
          ) : null}

          {!isProfileLoading &&
          profile &&
          !profileMatchesRegistration ? (
            <p className={styles.error} role="alert">
              Профиль не совпадает с профилем этой заявки.
              Перенос данных недоступен.
            </p>
          ) : null}

          {!isProfileLoading &&
          profile &&
          profileMatchesRegistration &&
          !isSupportedGender(profile.gender) ? (
            <p className={styles.error} role="alert">
              Сначала укажите корректный пол в своём профиле.
            </p>
          ) : null}

          <p>
            <Link to="/my/profile">Открыть мой профиль</Link>
          </p>

          <button
            type="button"
            disabled={isProfileLoading}
            onClick={() => {
              setRefreshGenderFromProfile(false)
              setProfileReload((value) => value + 1)
            }}
          >
            Обновить данные профиля
          </button>
        </div>

        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={refreshGenderFromProfile}
            disabled={!canCopyGender}
            onChange={(event) =>
              setRefreshGenderFromProfile(event.target.checked)
            }
          />
          <span>
            Подтверждаю перенос текущего пола из моего профиля
            в снимок этой заявки.
          </span>
        </label>

        <p className={styles.hint}>
          При отправке сервер заново прочитает профиль. Будет
          перенесён только пол; остальные данные снимка
          и заявочное время эта операция не изменяет.
        </p>

        {requiresGenderConsent ? (
          <p className={styles.hint}>
            Для этой заявки подтверждение переноса обязательно:
            в сохранённом снимке нет корректного значения пола.
          </p>
        ) : null}

        <label className={styles.field}>
          Ответ организатору
          <textarea
            required
            rows={4}
            maxLength={2000}
            value={reply}
            onChange={(event) => setReply(event.target.value)}
            placeholder="Напишите, что уточнили или исправили."
          />
        </label>

        <label className={styles.field}>
          Контактное лицо для экстренной связи
          <input
            required
            maxLength={200}
            value={contactName}
            onChange={(event) => setContactName(event.target.value)}
          />
        </label>

        <label className={styles.field}>
          Телефон для экстренной связи
          <input
            required
            type="tel"
            maxLength={50}
            value={contactPhone}
            onChange={(event) => setContactPhone(event.target.value)}
          />
        </label>

        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={medicalCertificate}
            onChange={(event) =>
              setMedicalCertificate(event.target.checked)
            }
          />
          <span>Подтверждаю наличие медицинской справки.</span>
        </label>

        <p className={styles.hint}>
          Файл справки здесь не загружается. Отметка подтверждает
          только заявленное вами наличие документа.
        </p>

        <label className={styles.checkbox}>
          <input
            required
            type="checkbox"
            checked={agreeToRules}
            onChange={(event) => setAgreeToRules(event.target.checked)}
          />
          <span>
            Подтверждаю достоверность данных и согласие с правилами
            участия.
          </span>
        </label>

        <button
          type="submit"
          className={styles.button}
          disabled={
            isSaving ||
            missingRequiredConsent ||
            (refreshGenderFromProfile && !canCopyGender)
          }
        >
          {isSaving
            ? 'Отправляем…'
            : 'Отправить на повторную проверку'}
        </button>
      </fieldset>

      {errorMessage ? (
        <p
          className={styles.error}
          role="alert"
          style={{ whiteSpace: 'pre-line' }}
        >
          {errorMessage}
        </p>
      ) : null}
    </form>
  )
}