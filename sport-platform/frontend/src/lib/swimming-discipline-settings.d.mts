export type SwimmingFields = { enabled: boolean; distance: string; stroke: string; gender: string; pool: string; categoryCodes: string }
export const SWIMMING_STROKES: [string, string][]
export function emptySwimmingFields(): SwimmingFields
export function buildSwimmingSettings(value: SwimmingFields): Record<string, unknown> | undefined

export function filterDisciplineCategoryInspection(inspection: import('./protocol-inspection.mjs').CategoryInspection, rawSettings: unknown): import('./protocol-inspection.mjs').CategoryInspection
