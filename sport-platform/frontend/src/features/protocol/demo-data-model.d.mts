export type DemoPlanInput = {
  eventId: string
  disciplineId: string
  ruleVersionId: string
  capacity: number
  count: number
  state: string
  datasetVersion: string
}
export function isMatchingDemoPlan(plan: DemoPlanInput | null, eventId: string, disciplineId: string): boolean
export function buildDemoRequest(plan: DemoPlanInput, requestId: string, confirmed: boolean): Readonly<{
  requestId: string
  expectedRuleVersionId: string
  expectedCapacity: number
  expectedCount: number
  datasetVersion: string
  confirmed: boolean
}>
