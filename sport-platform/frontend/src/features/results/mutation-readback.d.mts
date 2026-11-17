import type { CompetitionUnitDetailsResponse } from '../../api/results.api'
export function assertUnitSnapshot(value: unknown, unitId: string, expectedStatus?: string | null): CompetitionUnitDetailsResponse
export function mutateAndReadUnit(
  mutate: () => Promise<unknown>, read: () => Promise<unknown>,
  unitId: string, expectedStatus?: string | null,
): Promise<CompetitionUnitDetailsResponse>
