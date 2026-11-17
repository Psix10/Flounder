import type {
  CompetitionUnitEntryView, CompetitionUnitDetailsResponse,
  RecordResultRequest, ResultResponse, ResultType,
} from '../../api/results.api'
export type EditableStatus = 'PENDING' | 'DID_NOT_START' | 'DID_NOT_FINISH' | 'DISQUALIFIED'
export type Draft = { rawValue: string; status: EditableStatus; resultType: ResultType }
export type Drafts = Record<string, Draft>
export type Change = { readonly entryId: string; readonly participantName: string;
  readonly resultType: ResultType; readonly payload: RecordResultRequest }
export const SPECIAL_STATUSES: readonly string[]
export function isSpecial(status: string | null): boolean
export function canEditEntry(entry: CompetitionUnitEntryView): boolean
export function inferResultType(entries: CompetitionUnitEntryView[]): { type: ResultType; mixed: boolean }
export function draftFromEntry(entry: CompetitionUnitEntryView, fallback?: ResultType): Draft
export function createDrafts(entries: CompetitionUnitEntryView[], fallback?: ResultType): Drafts
export function sameDraft(left: Draft, right: Draft): boolean
export function isDirty(entry: CompetitionUnitEntryView, draft?: Draft, fallback?: ResultType): boolean
export function dirtyIds(entries: CompetitionUnitEntryView[], drafts: Drafts, fallback?: ResultType): string[]
export function validateDraft(draft: Draft): string | null
export function buildSavePlan(entries: CompetitionUnitEntryView[], drafts: Drafts, fallback?: ResultType, onlyIds?: string[] | null): {
  changes: Change[]; errors: Record<string, string>
}
export function mergeDrafts(before: CompetitionUnitEntryView[], after: CompetitionUnitEntryView[], drafts: Drafts, fallback?: ResultType): {
  drafts: Drafts; conflicts: string[]; orphaned: string[]
}
export function applyResultResponse(unit: CompetitionUnitDetailsResponse, response: ResultResponse): CompetitionUnitDetailsResponse
export function isComplete(entry: CompetitionUnitEntryView): boolean
export function saveSequentially(changes: Change[], save: (change: Change) => Promise<void>, shouldContinue?: () => boolean): Promise<{
  savedIds: string[]; failedId: string | null; error: unknown; remainingIds: string[]; stopped: boolean
}>
