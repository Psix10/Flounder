import { useEffect, useRef, useState } from 'react'

import {
  getCompatibleProtocolRules,
  getDisciplineRegulationConfiguration,
  type DisciplineRegulationConfiguration,
  type ProtocolRuleSet,
} from '../../api/protocol.api'
import { ApiError } from '../../api/http'

type Props = {
  disciplineId: string
  accessToken: string
  value: string
  onChange: (versionId: string) => void
}

type LoadState =
  | { kind: 'loading' }
  | {
      kind: 'bound'
      configuration: DisciplineRegulationConfiguration
    }
  | {
      kind: 'select'
      rules: ProtocolRuleSet[]
    }
  | {
      kind: 'error'
      message: string
    }

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к настройкам регламента или версиям правил.'
    }

    return error.message || 'Не удалось загрузить правила.'
  }

  return 'Не удалось загрузить правила. Проверьте соединение и повторите.'
}

export function SessionProtocolRuleSelect({
  disciplineId,
  accessToken,
  value,
  onChange,
}: Props) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [retry, setRetry] = useState(0)

  const changeRef = useRef(onChange)

  useEffect(() => {
    changeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    let active = true

    setState({ kind: 'loading' })
    changeRef.current('')

    async function load() {
      try {
        const configuration = await getDisciplineRegulationConfiguration(
          disciplineId,
          accessToken,
        )

        if (!active) {
          return
        }

        if (configuration.ruleSetVersionId) {
          setState({
            kind: 'bound',
            configuration,
          })
          return
        }

        const compatible = await getCompatibleProtocolRules(
          disciplineId,
          accessToken,
        )

        if (!active) {
          return
        }

        const published = compatible.filter(
          (rule) => rule.status === 'PUBLISHED',
        )

        setState({
          kind: 'select',
          rules: published,
        })
      } catch (error) {
        if (active) {
          setState({
            kind: 'error',
            message: errorMessage(error),
          })
        }
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [disciplineId, accessToken, retry])

  if (state.kind === 'bound') {
    const configuration = state.configuration

    return (
      <section aria-label="Правила дисциплины">
        <h4>Правила из регламента</h4>
        <p>
          {configuration.name}, версия регламента{' '}
          {configuration.versionNo}.
        </p>
        <p>
          При генерации сервер использует привязанную версию правил.
          Подмена другой версией недоступна.
        </p>

        {configuration.settings ? (
          <details>
            <summary>Сохранённые настройки регламента</summary>
            <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
              {JSON.stringify(configuration.settings, null, 2)}
            </pre>
          </details>
        ) : null}
      </section>
    )
  }

  if (state.kind === 'select') {
    return (
      <section aria-label="Выбор правил дисциплины">
        <label>
          Опубликованная версия правил
          <select
            required
            value={value}
            onChange={(event) => onChange(event.target.value)}
            style={{ width: '100%', padding: '0.75rem', font: 'inherit' }}
          >
            <option value="">Выберите версию правил</option>
            {state.rules.map((rule) => (
              <option
                key={rule.ruleSetVersionId}
                value={rule.ruleSetVersionId}
              >
                {rule.name} — версия {rule.versionNo}
              </option>
            ))}
          </select>
        </label>

        {state.rules.length === 0 ? (
          <p role="status">
            Доступных опубликованных версий нет. Настройте привязку
            регламента либо создайте и опубликуйте совместимые правила
            на странице стартового протокола дисциплины.
          </p>
        ) : (
          <p>
            У регламента нет привязанной версии правил. Выбранная версия
            будет явно передана в запросе генерации.
          </p>
        )}
      </section>
    )
  }

  return (
    <section aria-label="Загрузка правил дисциплины">
      <label>
        Опубликованная версия правил
        <select
          required
          value=""
          onChange={() => undefined}
          style={{ width: '100%', padding: '0.75rem', font: 'inherit' }}
        >
          <option value="">
            {state.kind === 'loading'
              ? 'Загружаем правила…'
              : 'Правила не загружены'}
          </option>
        </select>
      </label>

      {state.kind === 'loading' ? (
        <p role="status">Проверяем привязку к регламенту.</p>
      ) : (
        <>
          <p role="alert">{state.message}</p>
          <button
            type="button"
            onClick={() => setRetry((current) => current + 1)}
          >
            Повторить загрузку правил
          </button>
        </>
      )}
    </section>
  )
}