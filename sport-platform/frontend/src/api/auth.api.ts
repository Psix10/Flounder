import { apiFetch } from './http'
import type {
  LoginRequest,
  LoginResponse,
} from '../features/auth/auth.types'

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}