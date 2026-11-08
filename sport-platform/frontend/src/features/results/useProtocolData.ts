import { useEffect, useState } from 'react'
import { isMissingFinal } from './protocol-model.mjs'
import {
  getStandings, getFinalStandings, getFinalHistory,
  type Standings, type FinalStandings,
} from '../../api/standings.api'
import { getCompetitionUnitsByDiscipline, type CompetitionUnitResponse } from '../../api/results.api'

type Resource = 'current' | 'active' | 'history' | 'units'
type ProtocolData = {
  loading: boolean
  current: Standings | null
  active: FinalStandings | null
  history: FinalStandings[] | null
  units: CompetitionUnitResponse[] | null
  errors: Partial<Record<Resource, unknown>>
}
const emptyData = (): ProtocolData => ({ loading: true, current: null, active: null, history: null, units: null, errors: {} })

export function useProtocolData(eventId: string, disciplineId: string, accessToken?: string) {
  const [data, setData] = useState<ProtocolData>(emptyData)
  const [epoch, setEpoch] = useState(0)
  useEffect(() => {
    let valid = true
    const controller = new AbortController()
    setData(emptyData())
    async function load() {
      const [current, active, history, units] = await Promise.allSettled([
        getStandings(eventId, disciplineId, controller.signal),
        getFinalStandings(eventId, disciplineId, controller.signal).catch((error: unknown) => {
          if (isMissingFinal(error)) return null
          throw error
        }),
        accessToken ? getFinalHistory(eventId, disciplineId, accessToken, controller.signal) : Promise.resolve(null),
        accessToken ? getCompetitionUnitsByDiscipline(disciplineId, accessToken) : Promise.resolve(null),
      ])
      if (!valid) return
      const errors: ProtocolData['errors'] = {}
      if (current.status === 'rejected') errors.current = current.reason
      if (active.status === 'rejected') errors.active = active.reason
      if (history.status === 'rejected') errors.history = history.reason
      if (units.status === 'rejected') errors.units = units.reason
      setData({
        loading: false, errors,
        current: current.status === 'fulfilled' ? current.value : null,
        active: active.status === 'fulfilled' ? active.value : null,
        history: history.status === 'fulfilled' ? history.value : null,
        units: units.status === 'fulfilled' ? units.value : null,
      })
    }
    void load()
    return () => { valid = false; controller.abort() }
  }, [eventId, disciplineId, accessToken, epoch])
  function refresh() {
    setData(current => ({ ...current, loading: true }))
    setEpoch(current => current + 1)
  }
  return { data, refresh }
}
