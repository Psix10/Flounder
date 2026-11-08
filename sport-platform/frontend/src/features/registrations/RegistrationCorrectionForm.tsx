import { useRef, useState, type FormEvent } from 'react'

import { ApiError } from '../../api/http'
import { resubmitRegistration } from '../../api/registrations.api'
import type { Registration } from './registration.types'
import styles from './RegistrationCorrectionForm.module.css'

type Props = {
  registration: Registration
  accessToken: string
  onUpdated: (registration: Registration) => void
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
      return 'Статус заявки изменился. Обновите страницу.'
    }

    return error.message || 'Не удалось отправить исправления.'
  }

  return 'Не удалось отправить исправления. Попробуйте снова.'
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
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const submitting = useRef(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitting.current) {
      return
    }

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

    submitting.current = true
    setIsSaving(true)
    setErrorMessage(null)

    try {
      const updated = await resubmitRegistration(
        registration.id,
        {
          reply: normalizedReply,
          emergencyContactName: normalizedName,
          emergencyContactPhone: normalizedPhone,
          medicalCertificate,
          agreeToRules,
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
    <form className={styles.form} onSubmit={handleSubmit}>
      <h3 className={styles.title}>Ответить на уточнение</h3>

      <fieldset className={styles.fields} disabled={isSaving}>
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
          disabled={isSaving}
        >
          {isSaving
            ? 'Отправляем…'
            : 'Отправить на повторную проверку'}
        </button>
      </fieldset>

      {errorMessage ? (
        <p className={styles.error} role="alert">
          {errorMessage}
        </p>
      ) : null}
    </form>
  )
}