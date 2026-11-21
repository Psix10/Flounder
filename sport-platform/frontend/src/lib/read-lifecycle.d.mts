export function startRead<T>(
  read: () => Promise<T>,
  handlers: {
    success: (value: T) => void
    failure: (error: unknown) => void
    finished: () => void
  },
): () => void
