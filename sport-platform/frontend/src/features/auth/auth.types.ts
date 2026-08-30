export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export type AuthSession = {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: number
}


export type Role = 'platform_admin' | 'organizer' | 'operator' | 'participant'