import { apiFetch } from './http'
export type StandingsEntry = {
  overallPlace?: number | null
  competitionUnitId: string
  competitionUnitLabel: string
  unitPlace?: number | null
  participantName: string
  clubName?: string | null
  rawValue?: string | null
  resultType?: string | null
  status: string
  categoryCode?: string | null
  seedingGroupCode?: string | null
  categoryPlace?: number | null
}
export type Standings = {
  eventId: string
  eventDisciplineId: string
  resultType: string
  rankingStrategy: string
  tiePolicy: string
  entries: StandingsEntry[]
}
export type FinalStandings = {
  id: string
  eventId: string
  eventDisciplineId: string
  revision: number
  status: 'CONFIRMED' | 'REVOKED'
  confirmedAt: string
  confirmedBy: string
  confirmationNote?: string | null
  standings: Standings
  revokedAt?: string | null
  revokedBy?: string | null
  revocationReason?: string | null
}
export type ConfirmFinalRequest = { requestId: string; programmeComplete: true; note: string }
function scope(eventId: string, disciplineId: string) {
  return `events/${encodeURIComponent(eventId)}/disciplines/${encodeURIComponent(disciplineId)}`
}
function checkedStandings(data: Standings): Standings {
  if (!data || !Array.isArray(data.entries) || typeof data.eventId !== 'string' || typeof data.eventDisciplineId !== 'string')
    throw new Error('Сервер вернул неожиданный формат зачёта.')
  return data
}
function checkedFinal(data: FinalStandings): FinalStandings {
  if (!data || typeof data.id !== 'string' || !Number.isInteger(data.revision) || !['CONFIRMED','REVOKED'].includes(data.status))
    throw new Error('Сервер вернул неожиданный формат итогового снимка.')
  checkedStandings(data.standings)
  return data
}
export async function getStandings(eventId:string,disciplineId:string,signal?:AbortSignal):Promise<Standings> {
  return checkedStandings(await apiFetch<Standings>(`/api/v1/public/${scope(eventId,disciplineId)}/standings`,{signal}))
}
export async function getFinalStandings(eventId:string,disciplineId:string,signal?:AbortSignal):Promise<FinalStandings> {
  const data=checkedFinal(await apiFetch<FinalStandings>(`/api/v1/public/${scope(eventId,disciplineId)}/final-standings`,{signal}))
  if(data.status!=='CONFIRMED') throw new Error('Публичный endpoint вернул неактивную ревизию. Обновите данные.')
  return data
}
export async function getFinalHistory(eventId:string,disciplineId:string,accessToken:string,signal?:AbortSignal):Promise<FinalStandings[]> {
  const data=await apiFetch<FinalStandings[]>(`/api/v1/${scope(eventId,disciplineId)}/finalizations`,{accessToken,signal})
  if(!Array.isArray(data)) throw new Error('Неожиданный формат истории итогов.')
  return data.map(checkedFinal)
}
export async function confirmFinal(eventId:string,disciplineId:string,request:ConfirmFinalRequest,accessToken:string):Promise<FinalStandings> {
  return checkedFinal(await apiFetch<FinalStandings>(`/api/v1/${scope(eventId,disciplineId)}/finalizations`,{
    method:'POST',accessToken,body:JSON.stringify(request),
  }))
}
export async function revokeFinal(eventId:string,disciplineId:string,id:string,reason:string,accessToken:string):Promise<FinalStandings> {
  return checkedFinal(await apiFetch<FinalStandings>(`/api/v1/${scope(eventId,disciplineId)}/finalizations/${encodeURIComponent(id)}/revoke`,{
    method:'POST',accessToken,body:JSON.stringify({reason}),
  }))
}
