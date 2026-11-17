// SWIM_LABELS_V1
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/http'
import { withdrawConfirmedRegistration } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from './registration.types'
import {
  canOfferWithdrawal,
  createWithdrawalPayload,
  withdrawalFailure,
  withdrawalReasonError,
} from './registration-withdrawal.mjs'
import styles from './RegistrationWithdrawalForm.module.css'

type Props = {
  registration: Registration
  accessToken: string
  disabled?: boolean
  initiallyOpen?: boolean
  onUpdated: (registration: Registration) => void | Promise<void>
  onBusyChange?: (busy: boolean) => void
  beforeSubmit?: () => boolean
  onRefresh: () => void
  onCancel?: () => void
}

export function RegistrationWithdrawalForm({
  registration, accessToken, disabled = false, initiallyOpen = false,
  onUpdated, onBusyChange, beforeSubmit, onRefresh, onCancel,
}: Props) {
  const { roles } = useAuth()
  const [isOpen, setIsOpen] = useState(initiallyOpen)
  const [reason, setReason] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [requiresRefresh, setRequiresRefresh] = useState(false)
  const submitting = useRef(false)
  const mounted = useRef(false)
  const id = useId()
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  if (!canOfferWithdrawal(registration.status, roles)) return null

  const participantName = [
    registration.participantSnapshot?.lastName,
    registration.participantSnapshot?.firstName,
    registration.participantSnapshot?.middleName,
  ].filter(Boolean).join(' ') || 'Участник без указания ФИО'

  function cancel() {
    if (submitting.current) return
    setIsOpen(false)
    setReason('')
    setConfirmed(false)
    setErrorMessage(null)
    onCancel?.()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || disabled || requiresRefresh) return
    let request: { reason: string }
    try {
      request = createWithdrawalPayload(reason, confirmed)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Проверьте форму.')
      return
    }
    if (beforeSubmit && !beforeSubmit()) return
    submitting.current = true
    setIsSaving(true)
    setErrorMessage(null)
    onBusyChange?.(true)
    let updated: Registration
    try {
      updated = await withdrawConfirmedRegistration(registration.id, request, accessToken)
    } catch (error) {
      if (mounted.current) {
        const failure = withdrawalFailure(
          error instanceof ApiError ? error.status : undefined,
          error instanceof ApiError ? error.message : undefined,
        )
        const violations = error instanceof ApiError
          ? error.violations.map((item) => `${item.field}: ${item.message}`) : []
        setErrorMessage([failure.message, ...violations].join('\n'))
        setRequiresRefresh(failure.requiresRefresh)
      }
      submitting.current = false
      if (mounted.current) setIsSaving(false)
      onBusyChange?.(false)
      return
    }
    try {
      if (mounted.current) {
        setIsOpen(false)
        setReason('')
        setConfirmed(false)
        await onUpdated(updated)
      }
    } catch {
      if (mounted.current) {
        setIsOpen(true)
        setRequiresRefresh(true)
        setErrorMessage('Сервер выполнил снятие, но обновление экрана не завершилось. Обновите данные; повторять снятие не нужно.')
      }
    } finally {
      submitting.current = false
      if (mounted.current) setIsSaving(false)
      onBusyChange?.(false)
    }
  }

  if (!isOpen) {
    return (
      <div className={styles.controls}>
        <button type="button" className={styles.button} disabled={disabled || isSaving}
          onClick={() => setIsOpen(true)}>
          Снять с участия
        </button>
      </div>
    )
  }

  return (
    <form className={styles.panel} onSubmit={(event) => void submit(event)} aria-busy={isSaving}>
      <h3 className={styles.title}>Снять подтверждённую заявку с участия</h3>
      <p className={styles.hint}>Участник: {participantName}</p>
      <p className={styles.technical}>ID заявки: {registration.id}</p>
      <p className={styles.hint}>
        Заявка будет снята с участия. Если существует неоплаченный ручной платёж
        в статусе «Создан» без внешних платёжных реквизитов, сервер отменит его
        в той же операции. Записи и история сохранятся; снимок и время не изменятся.
      </p>
      <p className={styles.hint}>
        При оплаченном, ожидающем или внешнем платеже, а также при существующих
        протоколах или заплывах сервер отклонит снятие. Форма не подтверждает доступность операции.
      </p>
      <label className={styles.field} htmlFor={`${id}-reason`}>
        Причина снятия
        <textarea id={`${id}-reason`} required rows={4} maxLength={2000}
          value={reason} disabled={disabled || isSaving || requiresRefresh}
          onChange={(event) => { setReason(event.target.value); setConfirmed(false) }} />
      </label>
      <label className={styles.confirm}>
        <input type="checkbox" checked={confirmed} required
          disabled={disabled || isSaving || requiresRefresh}
          onChange={(event) => setConfirmed(event.target.checked)} />
        <span>
          Подтверждаю снятие участника «{participantName}» по заявке {registration.id}
          {' '}и отмену допустимого неоплаченного ручного платежа. Вернуть участие этой формой нельзя.
        </span>
      </label>
      <div className={styles.actions}>
        <button className={styles.button} type="submit"
          disabled={disabled || isSaving || requiresRefresh || !confirmed || Boolean(withdrawalReasonError(reason))}>
          {isSaving ? 'Снимаем заявку…' : 'Подтвердить снятие с участия'}
        </button>
        <button className={styles.secondary} type="button" disabled={disabled || isSaving || requiresRefresh} onClick={cancel}>
          Отмена
        </button>
        {requiresRefresh ? (
          <button className={styles.secondary} type="button" disabled={isSaving} onClick={onRefresh}>
            Обновить данные перед повторной попыткой
          </button>
        ) : null}
      </div>
      {errorMessage ? <p className={styles.error} role="alert">{errorMessage}</p> : null}
    </form>
  )
}
