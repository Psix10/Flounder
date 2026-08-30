const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type ApiErrorResponse = {
  code?: string
  message?: string
}

type ApiFetchOptions = RequestInit & {
  accessToken?: string | null
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  if (!API_BASE_URL) {
    throw new Error(
      'VITE_API_BASE_URL is not configured. Create frontend/.env.local and restart Vite.',
    )
  }

  const { accessToken, ...requestOptions } = options

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers: {
      Accept: 'application/json',
      ...(accessToken
        ? { Authorization: `Bearer ${accessToken}` }
        : {}),
      ...requestOptions.headers,
    },
  })

  if (!response.ok) {
    let payload: ApiErrorResponse | undefined

    try {
      payload = (await response.json()) as ApiErrorResponse
    } catch {
      payload = undefined
    }

    throw new ApiError(
      response.status,
      payload?.message ?? `Request failed with HTTP ${response.status}`,
      payload?.code,
    )
  }

  const contentType = response.headers.get('content-type')

  if (!contentType?.includes('application/json')) {
    return undefined as T
  }

  return (await response.json()) as T
}