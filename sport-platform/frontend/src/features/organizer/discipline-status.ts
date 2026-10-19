export function getDisciplineStatusLabel(status?: string | null): string {
  switch (status) {
    case 'DRAFT':
      return 'Черновик'
    case 'PUBLISHED':
      return 'Опубликована'
    case 'ARCHIVED':
      return 'В архиве'
    default:
      return status || 'Неизвестно'
  }
}

export function getDisciplineStatusTone(
  status?: string | null,
): 'draft' | 'published' | 'archived' | 'neutral' {
  switch (status) {
    case 'DRAFT':
      return 'draft'
    case 'PUBLISHED':
      return 'published'
    case 'ARCHIVED':
      return 'archived'
    default:
      return 'neutral'
  }
}