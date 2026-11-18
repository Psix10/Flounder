// DISCIPLINE_METADATA_ADMISSION_V1
import { filterDisciplineCategoryInspection } from '../../lib/swimming-discipline-settings.mjs'
import type { Registration } from '../registrations/registration.types'
import { formatRegistrationSeedTime } from '../registrations/registrationTime'
import {
  inspectCategories, previewCategory, disciplineFacts, snapshotYear, snapshotGender,
  configuredGenderWarning, savedCategoryLabel,
} from '../../lib/protocol-inspection.mjs'
import styles from './ProtocolInspection.module.css'

function name(registration: Registration): string {
  const person = registration.participantSnapshot
  return [person?.lastName, person?.firstName, person?.middleName].filter(Boolean).join(' ') || 'ФИО не указано'
}
function seed(registration?: Registration): string {
  return registration ? formatRegistrationSeedTime(registration.registrationMeta?.seedTimeSeconds) : 'Заявка недоступна'
}
export function ProtocolDisciplineFacts({ settings }: { settings: unknown }) {
  const facts = disciplineFacts(settings)
  return <div className={styles.block}>
    <dl className={styles.facts}>
      <div><dt>Дистанция</dt><dd>{facts.distance}</dd></div>
      <div><dt>Стиль</dt><dd>{facts.stroke}</dd></div>
      <div><dt>Пол в настройках дисциплины</dt><dd>{facts.gender === 'Код: OPEN' ? 'без ограничения' : facts.gender}</dd></div>
      <div><dt>Длина бассейна</dt><dd>{facts.pool}</dd></div>
    </dl>
    {facts.error ? <p className={styles.warning}>{facts.error}</p> : null}
    <p className={styles.note}>Дистанция, стиль, ограничение по полу и длина бассейна берутся из настроек дисциплины. Расписание заплывов здесь не проверяется.</p>
  </div>
}
export function ProtocolCategoryRules({ settings }: { settings: unknown }) {
  const inspection = inspectCategories(settings)
  const groups = new Set(inspection.categories.map(item => item.seedingGroupCode))
  return <div className={styles.block}>
    <h3>Возрастно-половые категории регламента</h3>
    <p className={inspection.state === 'ready' ? styles.note : styles.warning}>{inspection.message}</p>
    {inspection.state === 'ready' ? <>
      <div className={styles.scroll}><table className={styles.table}>
        <thead><tr><th>Категория</th><th>Условия</th><th>Группа распределения</th></tr></thead>
        <tbody>{inspection.categories.map(item => <tr key={item.code}>
          <td><code>{item.code}</code></td><td>{item.description}</td><td><code>{item.seedingGroupCode}</code></td>
        </tr>)}</tbody>
      </table></div>
      <p className={styles.note}>Категорий: {inspection.categories.length}. Групп распределения: {groups.size}. Категории с одинаковым кодом группы генератор распределяет совместно; с разными — отдельно.</p>
    </> : null}
    <p className={styles.note}>Это конфигурация регламента мероприятия, а не доказательство допуска в конкретную дисциплину. Совпадение категории не проверяет стиль, дистанцию и все ограничения заявки.</p>
  </div>
}
export function ConfirmedRegistrationAudit({ registrations, settings, disciplineSettings }: {
  registrations: Registration[]; settings: unknown; disciplineSettings: unknown;
}) {
  const inspection = filterDisciplineCategoryInspection(inspectCategories(settings), disciplineSettings)
  return <div className={styles.block}>
    <p className={styles.note}>Предварительная проверка категорий по снимкам заявок. Окончательное решение принимает сервер при генерации. Таблица не меняет заявки и не пересчитывает сохранённый протокол.</p>
    <div className={styles.scroll}><table className={styles.table}>
      <thead><tr><th>Участник / клуб</th><th>Год рождения</th><th>Пол в снимке</th><th>Категория — предварительно</th><th>Группа распределения</th><th>Заявочное время из заявки</th></tr></thead>
      <tbody>{registrations.map(registration => {
        const preview = previewCategory(registration.participantSnapshot, inspection)
        const warning = configuredGenderWarning(disciplineSettings, registration.participantSnapshot)
        return <tr key={registration.id}>
          <td>{name(registration)}<small>{registration.participantSnapshot?.clubName || 'Клуб не указан'}</small><small><code>{registration.id}</code></small></td>
          <td>{snapshotYear(registration.participantSnapshot)}</td><td>{snapshotGender(registration.participantSnapshot)}</td>
          <td><span className={preview.state === 'matched' ? styles.matched : styles.warning}>{preview.text}</span>{preview.code ? <small><code>{preview.code}</code></small> : null}{warning ? <p className={styles.warning}>{warning}</p> : null}</td>
          <td><code>{preview.seedingGroupCode ?? '—'}</code></td><td>{seed(registration)}</td>
        </tr>
      })}</tbody>
    </table></div>
    <p className={styles.note}>Здесь показано время, указанное в заявке. Для распределения по заплывам регламент может использовать другой показатель.</p>
  </div>
}
export function SavedParticipantFacts({ registration }: { registration?: Registration }) {
  if (!registration) return <small className={styles.saved}>Снимок заявки недоступен</small>
  return <small className={styles.saved}>Год рождения: {snapshotYear(registration.participantSnapshot)} · Пол: {snapshotGender(registration.participantSnapshot)} · Заявочное время из заявки: {seed(registration)}</small>
}
export function SavedCategoryDetails({ code, settings }: { code?: string | null; settings: unknown }) {
  const inspection = inspectCategories(settings)
  return <span>{savedCategoryLabel(code, inspection)}{code != null ? <small className={styles.saved}><code>{code}</code></small> : null}</span>
}
