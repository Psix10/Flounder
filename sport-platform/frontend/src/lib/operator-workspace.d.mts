import type { OperatorEvent, OperatorDiscipline } from '../api/operator-workspace.api'
import type { CompetitionUnitResponse } from '../api/results.api'
export function validateOperatorEvents(value: unknown): OperatorEvent[]
export function validateOperatorEvent(value: unknown, eventId: string): OperatorEvent
export function validateOperatorDisciplines(value: unknown, eventId: string): OperatorDiscipline[]
export function validateOperatorUnits(value: unknown, disciplineId: string): CompetitionUnitResponse[]
export function operatorEventPath(eventId: string): string
export function operatorDisciplinePath(eventId: string, disciplineId: string): string
export function operatorJudgePath(eventId: string, disciplineId: string, unitId: string): string
export function operatorResultsBackPath(eventId: string | null, disciplineId: string | null, actualDisciplineId?: string | null, isOperator?: boolean): string
