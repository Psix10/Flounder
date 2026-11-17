import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { ApiError } from '../../api/http'
import { createRegistration } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './RegistrationPage.module.css'

function parseSeedTimeSeconds(value: string): number | null {
  const normalized = value.trim().replace(',', '.')
  const match = /^(\d{1,3}):([0-5]\d)\.(\d{2})$/.exec(normalized)

  if (!match) {
    return null
  }

  const minutes = Number(match[1])
  const seconds = Number(match[2])
  const hundredths = Number(match[3])

  const totalHundredths =
    minutes * 6000 + seconds * 100 + hundredths

  if (totalHundredths <= 0) {
    return null
  }

  return totalHundredths / 100
}

function getRegistrationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Не удалось отправить заявку. Попробуйте ещё раз.'
  }

  const knownMessages: Record<string, string> = {
    'registrations.already_exists':
      'Вы уже подали заявку на эту дисциплину.',
    'registrations.discipline_limit_reached':
      'Свободных мест в дисциплине больше нет.',
    'registrations.discipline_not_available':
      'Эта дисциплина сейчас недоступна для регистрации.',
    'registrations.registration_not_open':
      'Регистрация на это событие сейчас не открыта.',
  }

  return knownMessages[error.code ?? ''] ?? error.message
}

export function RegistrationPage() {
  const { session } = useAuth()
  const { disciplineId, eventCode } = useParams()
  const navigate = useNavigate()

  const [seedTime, setSeedTime] = useState('')
  const [withoutSeedTime, setWithoutSeedTime] = useState(false)
  const [emergencyContactName, setEmergencyContactName] = useState('')
  const [emergencyContactPhone, setEmergencyContactPhone] = useState('')
  const [medicalCertificate, setMedicalCertificate] = useState(false)
  const [agreeToRules, setAgreeToRules] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const submitting = useRef(false)
  const accessToken = session?.accessToken

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', {
        replace: true,
        state: {
          from:
            eventCode && disciplineId
              ? `/events/${eventCode}/disciplines/${disciplineId}/register`
              : '/',
        },
      })
    }
  }, [accessToken, disciplineId, eventCode, navigate])

  if (!disciplineId || !accessToken) {
    return null
  }

  const registrationDisciplineId = disciplineId
  const authenticatedAccessToken = accessToken

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitting.current || isSubmitting) {
      return
    }

    setErrorMessage(null)

    const seedTimeSeconds = withoutSeedTime
      ? null
      : parseSeedTimeSeconds(seedTime)

    if (!withoutSeedTime && seedTimeSeconds === null) {
      setErrorMessage(
        'Укажите положительное время в формате ММ:СС.сс, ' +
          'например 01:25.34, или выберите «Без заявочного времени».',
      )
      return
    }

    const normalizedContactName = emergencyContactName.trim()
    const normalizedContactPhone = emergencyContactPhone.trim()

    if (!normalizedContactName || !normalizedContactPhone) {
      setErrorMessage(
        'Укажите имя и телефон контактного лица для экстренной связи.',
      )
      return
    }

    if (!agreeToRules) {
      setErrorMessage('Необходимо подтвердить согласие с правилами.')
      return
    }

    submitting.current = true
    setIsSubmitting(true)

    try {
      const registration = await createRegistration(
        {
          eventDisciplineId: registrationDisciplineId,
          registrationMeta: {
            emergencyContactName: normalizedContactName,
            emergencyContactPhone: normalizedContactPhone,
            medicalCertificate,
            agreeToRules,
            ...(seedTimeSeconds === null
              ? {}
              : { seedTimeSeconds }),
          },
        },
        authenticatedAccessToken,
      )

      navigate(`/my/registrations?created=${registration.id}`, {
        replace: true,
      })
    } catch (error) {
      setErrorMessage(getRegistrationErrorMessage(error))
    } finally {
      submitting.current = false
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link
        className={styles.backLink}
        to={eventCode ? `/events/${eventCode}` : '/'}
      >
        ← К соревнованию
      </Link>

      <div className={styles.card}>
        <p className={styles.eyebrow}>Подача заявки</p>

        <h1 className={styles.title}>Регистрация на дисциплину</h1>

        <p className={styles.description}>
          Укажите заявочное время, контакт на случай экстренной
          ситуации и подтвердите условия участия. После отправки
          статус заявки будет доступен в личном кабинете.
        </p>

        <form
          className={styles.form}
          onSubmit={(event) => void handleSubmit(event)}
        >
          <fieldset
            className={styles.fieldset}
            disabled={isSubmitting}
          >
            <legend className={styles.legend}>
              Заявочное время
            </legend>

            <label className={styles.field}>
              <span>Время на выбранную дисциплину</span>

              <input
                aria-describedby="seed-time-help"
                autoComplete="off"
                disabled={withoutSeedTime}
                maxLength={9}
                onChange={(event) => setSeedTime(event.target.value)}
                placeholder="01:25.34"
                required={!withoutSeedTime}
                type="text"
                value={seedTime}
              />
            </label>

            <p id="seed-time-help">
              Формат: минуты:секунды.сотые. Например, 01:25.34 —
              это 1 минута 25 секунд 34 сотых. Для времени меньше
              минуты укажите 00:45.12. Вместо точки можно использовать
              запятую.
            </p>

            <label className={styles.checkboxField}>
              <input
                checked={withoutSeedTime}
                onChange={(event) =>
                  setWithoutSeedTime(event.target.checked)
                }
                type="checkbox"
              />

              <span>Без заявочного времени (NT)</span>
            </label>
          </fieldset>

          <fieldset
            className={styles.fieldset}
            disabled={isSubmitting}
          >
            <legend className={styles.legend}>
              Контакт для экстренной связи
            </legend>

            <label className={styles.field}>
              <span>Контактное лицо</span>

              <input
                autoComplete="name"
                maxLength={200}
                onChange={(event) =>
                  setEmergencyContactName(event.target.value)
                }
                placeholder="Например, Анна Иванова"
                required
                value={emergencyContactName}
              />
            </label>

            <label className={styles.field}>
              <span>Телефон контактного лица</span>

              <input
                autoComplete="tel"
                inputMode="tel"
                maxLength={50}
                onChange={(event) =>
                  setEmergencyContactPhone(event.target.value)
                }
                placeholder="+7 900 000-00-00"
                required
                type="tel"
                value={emergencyContactPhone}
              />
            </label>
          </fieldset>

          <div className={styles.medicalNotice}>
            <strong>Медицинская справка</strong>

            <p>
              Подтвердите наличие действующей справки, если она
              требуется правилами выбранной дисциплины.
            </p>
          </div>

          <div className={styles.checkboxes}>
            <label className={styles.checkboxField}>
              <input
                checked={medicalCertificate}
                disabled={isSubmitting}
                onChange={(event) =>
                  setMedicalCertificate(event.target.checked)
                }
                type="checkbox"
              />

              <span>
                У меня есть действующая медицинская справка.
              </span>
            </label>

            <label className={styles.checkboxField}>
              <input
                checked={agreeToRules}
                disabled={isSubmitting}
                onChange={(event) =>
                  setAgreeToRules(event.target.checked)
                }
                required
                type="checkbox"
              />

              <span>
                Я согласен(на) с правилами соревнования и обработкой
                персональных данных.
              </span>
            </label>
          </div>

          {errorMessage ? (
            <p className={styles.errorMessage} role="alert">
              {errorMessage}
            </p>
          ) : null}

          <button
            className={styles.submitButton}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Отправляем…' : 'Подать заявку'}
          </button>
        </form>
      </div>
    </section>
  )
}