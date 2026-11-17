import { useRef, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from '../registrations/registration.types'
import { canOfferWithdrawal } from '../registrations/registration-withdrawal.mjs'
import { RegistrationWithdrawalForm } from '../registrations/RegistrationWithdrawalForm'
import { ReturnRegistrationForCorrectionForm } from './ReturnRegistrationForCorrectionForm'
import styles from '../registrations/RegistrationWithdrawalForm.module.css'

type Props = {
  registration: Registration
  accessToken: string
  onCorrection: (registration: Registration) => void
  onWithdrawal: (registration: Registration) => void
  onRefresh: () => void
}

export function RegistrationManagementActions({
  registration, accessToken, onCorrection, onWithdrawal, onRefresh,
}: Props) {
  const { roles } = useAuth()
  const [action, setAction] = useState<'correction' | 'withdrawal' | null>(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  if (!canOfferWithdrawal(registration.status, roles)) return null
  function beforeSubmit() { return !busyRef.current }
  function onBusyChange(value: boolean) {
    busyRef.current = value
    setBusy(value)
  }
  if (action === 'correction') {
    return (
      <ReturnRegistrationForCorrectionForm registration={registration} accessToken={accessToken}
        initiallyOpen onUpdated={onCorrection} onBusyChange={onBusyChange}
        beforeSubmit={beforeSubmit} onCancel={() => setAction(null)} />
    )
  }
  if (action === 'withdrawal') {
    return (
      <RegistrationWithdrawalForm registration={registration} accessToken={accessToken}
        initiallyOpen onUpdated={onWithdrawal} onBusyChange={onBusyChange}
        beforeSubmit={beforeSubmit} onRefresh={onRefresh} onCancel={() => setAction(null)} />
    )
  }
  return (
    <div className={styles.actions}>
      <button type="button" className={styles.secondary} disabled={busy}
        onClick={() => { if (!busyRef.current) setAction('correction') }}>
        Вернуть на уточнение
      </button>
      <button type="button" className={styles.button} disabled={busy}
        onClick={() => { if (!busyRef.current) setAction('withdrawal') }}>
        Снять с участия
      </button>
    </div>
  )
}
