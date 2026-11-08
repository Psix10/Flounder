import type { ProtocolCategory } from '../../api/regulations.api'
export type CategoryMode = 'NONE' | 'SEX_MIXED' | 'SEX_SEPARATE' | 'CUSTOM'
export function presetCategories(mode: Exclude<CategoryMode, 'CUSTOM'>): ProtocolCategory[]
export function parseCategoryJson(raw: string): ProtocolCategory[]
