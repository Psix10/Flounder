export type FieldViolation = { field: string; message: string }
export function readApiFailure(payload: unknown, status: number): {message:string;code?:string;violations:FieldViolation[]}
export function failureMessage(error: unknown, fallback?: string): string
export function isMissingFinal(error: unknown): boolean
export function keepAttemptAfterFailure(error: unknown): boolean
export function categoryOptions(entries: ReadonlyArray<{categoryCode?:string|null}>): string[]
export function filterStandings<T extends {categoryCode?:string|null}>(entries: ReadonlyArray<T>,category:string): T[]
export function formatTimestamp(value?:string|null): string
