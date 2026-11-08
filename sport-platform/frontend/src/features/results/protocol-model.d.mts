import type { StandingsEntry, ConfirmFinalRequest, FinalStandings } from '../../api/standings.api'
export const ALL_CATEGORIES: ':all:'
export const LEGACY_CATEGORY: ':legacy:'
export function isMissingFinal(error: unknown): boolean
export function isUncertainFailure(error: unknown): boolean
export function protocolErrorMessage(error: unknown): string
export function createConfirmationAttempt(note: string, programmeComplete: boolean, createId?: () => string): Readonly<ConfirmFinalRequest>
export type RevocationAttempt = Readonly<{ id: string; revision: number; reason: string }>
export function createRevocationAttempt(snapshot: Pick<FinalStandings, 'id' | 'revision'>, reason: string): RevocationAttempt
export function unitsReady(units: Array<{ status: string }> | null): boolean
export function canConfirm(flags: { permission: boolean; loading: boolean; busy: boolean; finalKnown: boolean; otherPending: boolean; pending: boolean; active: boolean; unitsReady: boolean; complete: boolean }): boolean
export function categoryOptions(entries: StandingsEntry[]): string[]
export function filterCategory(entries: StandingsEntry[], category: string): StandingsEntry[]
export function statusLabel(status: string): string
