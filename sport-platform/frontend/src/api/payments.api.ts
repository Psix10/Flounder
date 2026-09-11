import { apiFetch } from './http'

export type Payment = {
  id: string
  registrationId: string
  amount: number
  currency: string
  status: string
  provider: string
  providerPaymentId: string | null
  confirmationUrl: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Получает платёж текущего участника по его собственной заявке.
 */
export function getPaymentForRegistration(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/registrations/${encodeURIComponent(registrationId)}/payments`,
    { accessToken },
  )
}

/**
 * Создаёт платёж для собственной заявки участника.
 */
export function createPaymentForRegistration(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/registrations/${encodeURIComponent(registrationId)}/payments`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

/**
 * Получает данные платежа для проверки оператором / организатором.
 */
export function getPaymentForReview(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/payments/review/registrations/${encodeURIComponent(registrationId)}`,
    { accessToken },
  )
}

/**
 * Подтверждает платёж после проверки.
 */
export function confirmPayment(
  paymentId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/confirm`,
    {
      method: 'POST',
      accessToken,
    },
  )
}