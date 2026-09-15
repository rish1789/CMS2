// Client for POST /api/v1/staff/login
// See specs/004-staff-onboarding-direct-hire/contracts/staff-onboarding.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface LoginStaffRequest {
  identifier: string
  password: string
}

// 040-super-admin-rbac-login: this endpoint now also resolves the configured Super Admin
// credential. `accountId` is null and `email` holds the Super Admin's configured
// username (never a real email) when `role` is 'SUPER_ADMIN' - no Account row exists.
export interface LoginStaffResponse {
  token: string
  accountId: string | null
  email: string
  role: 'STAFF' | 'SUPER_ADMIN'
}

// _diagnostics [MEDIUM] - [STAFF_LOGIN] - [TYPE_MISMATCH]: POST /api/v1/staff/login never actually
// returns "UNAUTHORIZED" - that code belongs to a different, JWT-gated endpoint family
// (StaffAuthenticationEntryPoint). This endpoint returns INVALID_CREDENTIALS (401) or
// MISSING_REQUIRED_FIELD (400).
export type LoginStaffErrorBody =
  | { error: 'INVALID_CREDENTIALS'; message?: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field?: string; message?: string }

export class LoginStaffApiError extends Error {
  readonly body: LoginStaffErrorBody

  constructor(body: LoginStaffErrorBody) {
    super(body.message ?? 'Invalid email or password.')
    this.name = 'LoginStaffApiError'
    this.body = body
  }
}

export async function loginStaff(payload: LoginStaffRequest): Promise<LoginStaffResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/staff/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let body: LoginStaffErrorBody
    try {
      body = (await response.json()) as LoginStaffErrorBody
    } catch {
      body = { error: 'INVALID_CREDENTIALS' }
    }
    throw new LoginStaffApiError(body)
  }

  return (await response.json()) as LoginStaffResponse
}
