// Client for POST /api/v1/staff/login
// See specs/004-staff-onboarding-direct-hire/contracts/staff-onboarding.md

import type { LoginLockedBody } from '../../lib/loginLockout'

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
// (StaffAuthenticationEntryPoint).
// 075-login-hardening (D-3C-2): one INVALID_CREDENTIALS (401) for an unknown identifier and a wrong
// password alike - the earlier ACCOUNT_NOT_FOUND / INCORRECT_PASSWORD split revealed which emails and
// staff codes exist. 5 failures lock the identifier (TOO_MANY_LOGIN_ATTEMPTS, 429); a correct
// password on an account with no active clinic role gets NO_ACTIVE_CLINIC_ACCESS (403).
export type LoginStaffErrorBody =
  | { error: 'INVALID_CREDENTIALS'; message?: string }
  | LoginLockedBody
  | { error: 'NO_ACTIVE_CLINIC_ACCESS'; message?: string }
  | { error: 'RATE_LIMIT_EXCEEDED'; message?: string }
  // 062-rejected-clinic-gating FR-007: every role this account holds is Doctor/Operations at a rejected clinic.
  | { error: 'CLINIC_NOT_ACTIVE'; message?: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field?: string; message?: string }

export class LoginStaffApiError extends Error {
  readonly body: LoginStaffErrorBody

  constructor(body: LoginStaffErrorBody) {
    super(body.message ?? 'Incorrect email, staff code or password.')
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
      // Response body didn't parse as JSON (e.g. a proxy error page) - don't claim the
      // credentials were wrong when we can't tell.
      body = { error: 'INVALID_CREDENTIALS', message: 'Could not sign in. Please try again.' }
    }
    throw new LoginStaffApiError(body)
  }

  return (await response.json()) as LoginStaffResponse
}
