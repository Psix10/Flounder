export type EventDraft = {
  title: string
  description: string
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
}

const PREFIX = 'flounder.event-draft.v1:'

const fields: (keyof EventDraft)[] = [
  'title',
  'description',
  'registrationOpenAt',
  'registrationCloseAt',
  'eventStartAt',
  'eventEndAt',
  'organizationId',
  'venueId',
  'sportId',
  'regulationVersionId',
]

export function getEventDraftOwner(
  accessToken: string | undefined,
): string | null {
  if (!accessToken) {
    return null
  }

  try {
    const payload = accessToken.split('.')[1]

    if (!payload) {
      return null
    }

    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(
      Math.ceil(base64.length / 4) * 4,
      '=',
    )

    const json = decodeURIComponent(
      Array.from(atob(padded))
        .map(
          (character) =>
            `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`,
        )
        .join(''),
    )

    const decoded: unknown = JSON.parse(json)

    if (
      typeof decoded !== 'object' ||
      decoded === null ||
      !('sub' in decoded) ||
      typeof decoded.sub !== 'string' ||
      !decoded.sub.trim()
    ) {
      return null
    }

    return decoded.sub
  } catch {
    return null
  }
}

function storageKey(owner: string): string {
  return PREFIX + encodeURIComponent(owner)
}

export function readEventDraft(
  owner: string | null,
): EventDraft | null {
  if (!owner) {
    return null
  }

  try {
    const raw = sessionStorage.getItem(storageKey(owner))

    if (!raw) {
      return null
    }

    const parsed: unknown = JSON.parse(raw)

    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      !('version' in parsed) ||
      parsed.version !== 1 ||
      !('form' in parsed) ||
      typeof parsed.form !== 'object' ||
      parsed.form === null
    ) {
      return null
    }

    const form = parsed.form as Record<string, unknown>

    if (
      !fields.every(
        (field) =>
          typeof form[field] === 'string' &&
          form[field].length <= 100_000,
      )
    ) {
      return null
    }

    return Object.fromEntries(
      fields.map((field) => [field, form[field]]),
    ) as EventDraft
  } catch {
    return null
  }
}

export function saveEventDraft(
  owner: string,
  form: EventDraft,
): boolean {
  try {
    sessionStorage.setItem(
      storageKey(owner),
      JSON.stringify({
        version: 1,
        form,
      }),
    )

    return true
  } catch {
    return false
  }
}

export function clearEventDraft(owner: string): boolean {
  try {
    sessionStorage.removeItem(storageKey(owner))
    return true
  } catch {
    return false
  }
}