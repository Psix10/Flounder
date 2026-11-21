import type { CategoryInspection } from './protocol-inspection.mjs'

export function readEventCategories(
  versionId: string | null | undefined,
  readConfiguration: (id: string) => Promise<{
    document: { id: string }
    protocol: unknown
  }>,
  formatError: (error: unknown, fallback: string) => string,
): Promise<CategoryInspection>
