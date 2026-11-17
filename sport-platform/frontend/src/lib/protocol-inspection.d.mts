export type CategoryView = { code: string; seedingGroupCode: string; description: string }
export type CategoryInspection = { state: 'unavailable' | 'invalid' | 'disabled' | 'ready'; message: string; categories: CategoryView[] }
export type CategoryPreview = { state: string; text: string; code?: string; seedingGroupCode?: string }
export function parseCalendarDate(value: unknown): { year: number; month: number; day: number; iso: string } | null
export function snapshotYear(snapshot: unknown): string
export function snapshotGender(snapshot: unknown): string
export function inspectCategories(settings: unknown): CategoryInspection
export function previewCategory(snapshot: unknown, inspection: CategoryInspection): CategoryPreview
export function disciplineFacts(raw: unknown): { error: string | null; distance: string; stroke: string; gender: string; pool: string }
export function configuredGenderWarning(raw: unknown, snapshot: unknown): string | null
export function savedCategoryLabel(code: string | null | undefined, inspection: CategoryInspection): string
