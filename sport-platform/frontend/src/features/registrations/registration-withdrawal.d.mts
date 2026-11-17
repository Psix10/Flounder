export const REGISTRATION_STATUS_LABELS: Readonly<Record<string, string>>
export function registrationStatusLabel(status: string): string
export function canOfferWithdrawal(status: string, roles: readonly string[]): boolean
export function withdrawalReasonError(reason: unknown): string | null
export function createWithdrawalPayload(reason: string, confirmed: boolean): { reason: string }
export function withdrawalFailure(status: number | undefined, message?: string): {
  message: string
  requiresRefresh: boolean
}
export type WithdrawalHistoryView = {
  operation: 'WITHDRAW_CONFIRMED_REGISTRATION'
  fromStatus: 'CONFIRMED'
  toStatus: 'WITHDRAWN'
  reason: string
  actorUserId: string
  occurredAt: string
}
export function readWithdrawalHistory(value: unknown): WithdrawalHistoryView[]
