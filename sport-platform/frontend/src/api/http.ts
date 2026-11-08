import { readApiFailure } from '../lib/standings-ui.mjs'
import type { FieldViolation } from '../lib/standings-ui.mjs'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export class ApiError extends Error {
  public readonly status: number
  public readonly code?: string
  public readonly violations: FieldViolation[]

  constructor(
    status: number,
    message: string,
    code?: string,
    violations: FieldViolation[] = [],
  ) {
    super(message)

    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.violations = violations
  }
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
  const headers = new Headers(requestOptions.headers)

  headers.set('Accept', 'application/json')

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  if (
    typeof requestOptions.body === 'string' &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers,
  })

  if (!response.ok) {
    let payload: unknown

    try {
      payload = await response.json()
    } catch {
      payload = undefined
    }

    const failure = readApiFailure(payload, response.status)
    throw new ApiError(response.status, failure.message, failure.code, failure.violations)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type')

  if (!contentType?.includes('application/json')) {
    return undefined as T
  }

  return (await response.json()) as T
}
