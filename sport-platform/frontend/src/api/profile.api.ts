import { apiFetch } from './http'

export type ParticipantGender = 'MALE' | 'FEMALE'

export type MyProfile = {
  id: string
  userId: string
  firstName: string
  lastName: string
  middleName: string | null
  birthDate: string
  gender: string | null
  city: string | null
  countryCode: string | null
  clubName: string | null
  updatedAt: string
}

export function getMyProfile(
  accessToken: string,
  signal?: AbortSignal,
): Promise<MyProfile> {
  return apiFetch<MyProfile>('/api/v1/profile/me', {
    accessToken,
    signal,
  })
}

export function updateMyProfile(
  accessToken: string,
  gender: ParticipantGender,
  signal?: AbortSignal,
): Promise<MyProfile> {
  return apiFetch<MyProfile>('/api/v1/profile/me', {
    method: 'PATCH',
    accessToken,
    signal,
    body: JSON.stringify({ gender }),
  })
}