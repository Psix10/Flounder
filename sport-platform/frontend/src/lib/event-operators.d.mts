import type { EventOperatorMember, EventOperatorCandidate } from '../api/event-operators.api'
export function normalizeOperatorEmail(input: string): string
export function validateOperatorMembers(value: unknown, eventId: string): EventOperatorMember[]
export function validateOperatorCandidates(value: unknown, requestedEmail: string): EventOperatorCandidate[]
export function changeAndReadOperators(
  write: () => Promise<unknown>, read: () => Promise<unknown>, eventId: string,
  operatorUserId: string, expectedAssigned: boolean,
): Promise<EventOperatorMember[]>
