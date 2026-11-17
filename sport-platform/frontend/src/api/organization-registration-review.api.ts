import { apiFetch } from './http'
import type {
  OrganizationRegistrationResponse,
} from './organizations.api'

export type OrganizationRegistrationReview =
  OrganizationRegistrationResponse & {
    organizationId?: string | null
  }

export type OrganizationReviewStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'

export function getMyOrganizationRegistrations(
  accessToken: string,
  page = 0,
  size = 20,
): Promise<OrganizationRegistrationReview[]> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  return apiFetch<OrganizationRegistrationReview[]>(
    `/api/v1/organization-registration-requests/me?${query}`,
    { accessToken },
  )
}

export function getOrganizationRegistrations(
  accessToken: string,
  status: OrganizationReviewStatus = 'PENDING',
  page = 0,
  size = 20,
): Promise<OrganizationRegistrationReview[]> {
  const query = new URLSearchParams({
    status,
    page: String(page),
    size: String(size),
  })

  return apiFetch<OrganizationRegistrationReview[]>(
    `/api/v1/organization-registration-requests?${query}`,
    { accessToken },
  )
}

export function approveOrganizationRegistration(
  id: string,
  accessToken: string,
): Promise<OrganizationRegistrationReview> {
  return apiFetch<OrganizationRegistrationReview>(
    `/api/v1/organization-registration-requests/${encodeURIComponent(id)}/approve`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export function rejectOrganizationRegistration(
  id: string,
  reason: string,
  accessToken: string,
): Promise<OrganizationRegistrationReview> {
  return apiFetch<OrganizationRegistrationReview>(
    `/api/v1/organization-registration-requests/${encodeURIComponent(id)}/reject`,
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify({ reason }),
    },
  )
}