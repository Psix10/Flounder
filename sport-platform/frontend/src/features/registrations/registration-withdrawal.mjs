// SWIM_LABELS_V1
export const REGISTRATION_STATUS_LABELS = Object.freeze({
  SUBMITTED: 'На рассмотрении',
  CONFIRMED: 'Подтверждена',
  NEEDS_CORRECTION: 'Требует уточнения',
  REJECTED: 'Отклонена',
  CANCELLED: 'Отменена',
  WITHDRAWN: 'Снята с участия',
})

export function registrationStatusLabel(status) {
  return Object.hasOwn(REGISTRATION_STATUS_LABELS, status)
    ? REGISTRATION_STATUS_LABELS[status]
    : status
}

export function canOfferWithdrawal(status, roles) {
  return status === 'CONFIRMED' && Array.isArray(roles) &&
    roles.some((role) => role === 'organizer' || role === 'platform_admin')
}

export function withdrawalReasonError(reason) {
  if (typeof reason !== 'string' || !reason.trim()) {
    return 'Укажите причину снятия с участия.'
  }
  if (reason.length > 2000) {
    return 'Причина должна содержать не более 2000 символов.'
  }
  return null
}

export function createWithdrawalPayload(reason, confirmed) {
  const error = withdrawalReasonError(reason)
  if (error) throw new Error(error)
  if (confirmed !== true) {
    throw new Error('Подтвердите снятие выбранного участника и отмену допустимого неоплаченного ручного платежа.')
  }
  return { reason: reason.trim() }
}

export function withdrawalFailure(status, message) {
  const prefix = status === 401
    ? 'Сессия истекла. Войдите снова и обновите данные.'
    : status === 403
      ? 'Нет прав на снятие заявки этого события.'
      : status === 404
        ? 'Заявка не найдена. Обновите данные.'
        : status === 409
          ? 'Снятие недоступно: проверьте актуальный статус заявки, платёж и наличие протоколов или заплывов.'
          : status === 400
            ? 'Проверьте причину снятия и подтверждение операции.'
            : 'Не удалось подтвердить результат операции. Перед повторной отправкой обновите данные: заявка уже могла быть снята.'
  return {
    message: typeof message === 'string' && message.trim()
      ? `${prefix}\nОтвет сервера: ${message}` : prefix,
    requiresRefresh: status !== 400,
  }
}

export function readWithdrawalHistory(value) {
  if (!Array.isArray(value)) return []
  return value.filter((item) => item !== null && typeof item === 'object' &&
    !Array.isArray(item) && item.operation === 'WITHDRAW_CONFIRMED_REGISTRATION' &&
    item.fromStatus === 'CONFIRMED' && item.toStatus === 'WITHDRAWN' &&
    typeof item.reason === 'string' && typeof item.actorUserId === 'string' &&
    typeof item.occurredAt === 'string')
}
