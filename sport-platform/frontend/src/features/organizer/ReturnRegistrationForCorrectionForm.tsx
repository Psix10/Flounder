import { useRef, useState, type FormEvent } from 'react'

import { ApiError } from '../../api/http'
import { returnRegistrationForCorrection } from '../../api/registrations.api'
import type { Registration } from '../registrations/registration.types'
import styles from '../registrations/RegistrationCorrectionForm.module.css'

type Props = {
  registration: Registration
  accessToken: string
  onUpdated: (registration: Registration) => void
  disabled?: boolean
  initiallyOpen?: boolean
  onBusyChange?: (busy: boolean) => void
  beforeSubmit?: () => boolean
  onCancel?: () => void
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите снова.'
    }

    if (error.status === 403) {
      return 'Нет прав на изменение заявок этого события.'
    }

    if (error.status === 409) {
      return (
        'Возврат недоступен: статус мог измениться, либо уже существует ' +
        'платёж, протокол или соревновательные единицы. ' +
        'Обновите список и проверьте связанные данные.'
      )
    }

    if (error.status === 400) {
      return [
        error.message || 'Проверьте причину возврата.',
        ...error.violations.map(
          (violation) => `${violation.field}: ${violation.message}`,
        ),
      ].join('\n')
    }

    return error.message || 'Не удалось вернуть заявку на уточнение.'
  }

  return (
    'Не удалось получить ответ сервера. Перед повторной отправкой ' +
    'обновите список и проверьте текущий статус заявки.'
  )
}

export function ReturnRegistrationForCorrectionForm({
  registration,
  accessToken,
  onUpdated,
  disabled = false,
  initiallyOpen = false,
  onBusyChange,
  beforeSubmit,
  onCancel,
}: Props) {
  const [isOpen, setIsOpen] = useState(initiallyOpen)
  const [reviewNote, setReviewNote] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const submitting = useRef(false)

  if (registration.status !== 'CONFIRMED') {
    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitting.current || disabled) {
      return
    }

    const note = reviewNote.trim()

    if (!note || note.length > 2000) {
      setErrorMessage('Укажите причину возврата: от 1 до 2000 символов.')
      return
    }

    if (beforeSubmit && !beforeSubmit()) return
    submitting.current = true
    setIsSaving(true)
    onBusyChange?.(true)
    setErrorMessage(null)

    try {
      const updated = await returnRegistrationForCorrection(
        registration.id,
        note,
        accessToken,
      )

      setIsOpen(false)
      setReviewNote('')
      onUpdated(updated)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      submitting.current = false
      setIsSaving(false)
      onBusyChange?.(false)
    }
  }

  if (!isOpen) {
    return (
      <div className={styles.form}>
        <button
          className={styles.button}
          type="button"
          disabled={disabled}
          onClick={() => {
            setErrorMessage(null)
            setIsOpen(true)
          }}
        >
          Вернуть на уточнение
        </button>
      </div>
    )
  }

  return (
    <form
      className={styles.form}
      onSubmit={(event) => void handleSubmit(event)}
    >
      <h3 className={styles.title}>
        Вернуть подтверждённую заявку на уточнение
      </h3>

      <p className={styles.hint}>
        Участник должен будет отправить исправления, после чего
        заявку потребуется рассмотреть повторно. Сам возврат
        не меняет снимок участника и заявочное время.
      </p>

      <fieldset className={styles.fields} disabled={isSaving || disabled}>
        <label className={styles.field}>
          Причина возврата
          <textarea
            required
            rows={4}
            maxLength={2000}
            value={reviewNote}
            onChange={(event) => setReviewNote(event.target.value)}
            placeholder="Укажите, какие данные нужно проверить или исправить."
          />
        </label>

        <p className={styles.hint}>
          При наличии платежа, сохранённого протокола или
          соревновательных единиц сервер отклонит возврат.
        </p>

        <button
          className={styles.button}
          disabled={!reviewNote.trim()}
          type="submit"
        >
          {isSaving ? 'Возвращаем…' : 'Подтвердить возврат на уточнение'}
        </button>

        <button
          type="button"
          onClick={() => {
            setIsOpen(false)
            setErrorMessage(null)
            onCancel?.()
          }}
        >
          Отмена
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
