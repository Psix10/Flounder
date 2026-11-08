import type { CorrectionHistoryItem } from './registration.types'
import styles from './RegistrationCorrectionHistory.module.css'

type Props = {
  items?: CorrectionHistoryItem[]
}

function formatDate(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return '—'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function RegistrationCorrectionHistory({ items }: Props) {
  if (!items?.length) {
    return null
  }

  return (
    <section className={styles.history}>
      <h3 className={styles.title}>История уточнений</h3>

      {items.map((item, index) => (
        <article
          className={styles.item}
          key={`${item.repliedAt}:${index}`}
        >
          <p className={styles.label}>Замечание организатора</p>
          <p className={styles.text}>{item.reviewNote || '—'}</p>

          <p className={styles.label}>Ответ участника</p>
          <p className={styles.text}>{item.reply}</p>

          <p className={styles.date}>
            Отправлен: {formatDate(item.repliedAt)}
          </p>
        </article>
      ))}
    </section>
  )
}