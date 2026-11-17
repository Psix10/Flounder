export type RegistrationGenderChange = {
  source: 'PARTICIPANT_PROFILE'
  sourceProfileId: string
  confirmedByUserId: string
  confirmedAt: string
  oldValue: unknown
  newValue: string
}



export type CorrectionHistoryItem = {
  reviewNote: string | null
  reviewedAt: string | null
  reviewedByUserId: string | null
  reply: string
  repliedAt: string
  repliedByUserId?: string
  genderChange?: RegistrationGenderChange
}

export type RegistrationWithdrawalHistoryItem = {
  operation: 'WITHDRAW_CONFIRMED_REGISTRATION'
  actorUserId: string
  occurredAt: string
  fromStatus: 'CONFIRMED'
  toStatus: 'WITHDRAWN'
  reason: string
  previousReviewNote?: string | null
  previousReviewedByUserId?: string | null
  previousReviewedAt?: string | null
}

export type RegistrationMeta = {
  emergencyContactName: string
  emergencyContactPhone: string
  medicalCertificate: boolean
  agreeToRules: boolean
  seedTimeSeconds?: number | null
  correctionHistory?: CorrectionHistoryItem[]
  withdrawalHistory?: RegistrationWithdrawalHistoryItem[]
  confirmedCorrection?: {
    reservePlace: boolean
    returnedAt: string
    returnedByUserId: string
  }
}

export type ParticipantSnapshot = {
  firstName: string
  lastName: string
  middleName: string | null
  birthDate: string | null
  gender: string | null
  city: string | null
  countryCode: string | null
  clubName: string | null
  sportMeta: Record<string, unknown>
}

export type CreateRegistrationRequest = {
  eventDisciplineId: string
  registrationMeta: {
    emergencyContactName: string
    emergencyContactPhone: string
    medicalCertificate: boolean
    agreeToRules: boolean
    seedTimeSeconds?: number
  }
}

export type ResubmitRegistrationRequest = {
  reply: string
  emergencyContactName: string
  emergencyContactPhone: string
  medicalCertificate: boolean
  agreeToRules: boolean
  refreshGenderFromProfile: boolean
}

export type RegistrationStatus =
  | 'SUBMITTED'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'NEEDS_CORRECTION'
  | 'CANCELLED'
  | 'WITHDRAWN'

export type Registration = {
  id: string
  eventId: string
  eventDisciplineId: string
  participantUserId: string
  participantProfileId: string
  status: RegistrationStatus
  participantSnapshot: ParticipantSnapshot
  registrationMeta: RegistrationMeta
  reviewNote: string | null
  reviewedByUserId: string | null
  reviewedAt: string | null
  submittedAt: string
  createdAt: string
  updatedAt: string
}
