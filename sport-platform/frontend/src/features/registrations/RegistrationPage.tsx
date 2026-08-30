import { FormEvent, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { createRegistration } from '../../api/registrations.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'

function getRegistrationErrorMessage(error: unknown) {
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

  const [emergencyContactName, setEmergencyContactName] = useState('')
  const [emergencyContactPhone, setEmergencyContactPhone] = useState('')
  const [medicalCertificate, setMedicalCertificate] = useState(false)
  const [agreeToRules, setAgreeToRules] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!disciplineId || !session) {
    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)

    if (!agreeToRules) {
      setErrorMessage('Необходимо подтвердить согласие с правилами.')
      return
    }

    setIsSubmitting(true)

    try {
      const registration = await createRegistration(
        {
          eventDisciplineId: disciplineId,
          registrationMeta: {
            emergencyContactName: emergencyContactName.trim(),
            emergencyContactPhone: emergencyContactPhone.trim(),
            medicalCertificate,
            agreeToRules,
          },
        },
        session.accessToken,
      )

      navigate(`/my/registrations?created=${registration.id}`, {
        replace: true,
      })
    } catch (error) {
      setErrorMessage(getRegistrationErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="registration-section">
      <Link className="back-link" to={`/events/${eventCode ?? ''}`}>
        ← К соревнованию
      </Link>

      <div className="auth-card registration-card">
        <p className="eyebrow">Подача заявки</p>

        <h1>Регистрация на дисциплину</h1>

        <p className="page-description">
          Укажите контакт на случай экстренной ситуации и подтвердите условия
          участия.
        </p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            <span>Контактное лицо</span>
            <input
              disabled={isSubmitting}
              maxLength={200}
              onChange={(event) =>
                setEmergencyContactName(event.target.value)
              }
              required
              value={emergencyContactName}
            />
          </label>

          <label>
            <span>Телефон контактного лица</span>
            <input
              disabled={isSubmitting}
              inputMode="tel"
              maxLength={50}
              onChange={(event) =>
                setEmergencyContactPhone(event.target.value)
              }
              required
              type="tel"
              value={emergencyContactPhone}
            />
          </label>

          <label className="checkbox-field">
            <input
              checked={medicalCertificate}
              disabled={isSubmitting}
              onChange={(event) =>
                setMedicalCertificate(event.target.checked)
              }
              type="checkbox"
            />
            <span>У меня есть действующая медицинская справка.</span>
          </label>

          <label className="checkbox-field">
            <input
              checked={agreeToRules}
              disabled={isSubmitting}
              onChange={(event) => setAgreeToRules(event.target.checked)}
              required
              type="checkbox"
            />
            <span>Я согласен(на) с правилами соревнования и обработкой данных.</span>
          </label>

          {errorMessage ? (
            <p className="form-error" role="alert">
              {errorMessage}
            </p>
          ) : null}

          <button
            className="button button-primary"
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