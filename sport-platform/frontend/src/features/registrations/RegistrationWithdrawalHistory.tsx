import { readWithdrawalHistory } from './registration-withdrawal.mjs'
import styles from './RegistrationWithdrawalForm.module.css'

export function RegistrationWithdrawalHistory({ items }: { items: unknown }) {
  const history = readWithdrawalHistory(items)
  if (!history.length) return null
  function date(value: string) {
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('ru-RU', {
      dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Moscow',
    }).format(parsed)
  }
  return (
    <section className={styles.history} aria-label="История снятия с участия">
      <h3 className={styles.title}>История снятия с участия</h3>
      <ol>
        {history.map((item, index) => (
          <li key={`${item.occurredAt}:${index}`}>
            <p>Подтверждена → Снята с участия</p>
            <p>Причина: {item.reason}</p>
            <p>Дата: {date(item.occurredAt)}</p>
            <details><summary>Автор операции</summary><p>{item.actorUserId}</p></details>
          </li>
        ))}
      </ol>
    </section>
  )
}
