// Client for POST /api/v1/clinics/{clinicId}/staff
// See specs/004-staff-onboarding-direct-hire/contracts/staff-onboarding.md
//
// Requires the STAFF-audience bearer token from staff-login (passed explicitly by the
// caller, same "caller owns where credentials live" approach as clinic-verification's
// AdminCredentials parameter).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

// 054-forms-validation-consistency: onboardStaff migrated onto the shared apiClient - this
// function's own error path had the identical defaultMessageFor(body) ?? body.message
// dead-code bug 043 found and fixed elsewhere, discarding both the backend's real message AND
// its `field` (used by OnboardStaffForm to map MISSING_REQUIRED_FIELD/INVALID_MOBILE_NUMBER to
// the right input). deactivateStaff/DeactivateStaffApiError below are untouched - out of this
// feature's scope (EmployeeModal.tsx, not one of the 5 named forms).
import { apiRequest } from '../../lib/apiClient'

export type StaffRole = 'Doctor' | 'Operations'

export interface OnboardStaffRequest {
  name: string
  email: string
  mobile?: string
  role: StaffRole
  doctor?: {
    specialization: string
    licenseNumber: string
    experienceYears: number
  }
}

export interface OnboardStaffResponse {
  accountId: string
  email: string
  staffCode: string
  temporaryPassword: string | null
  role: StaffRole
  doctorProfileId: string | null
  existingAccount: boolean
}

export type OnboardStaffErrorBody =
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'INVALID_ROLE'; message?: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field: string; message?: string }
  | { error: 'INVALID_MOBILE_NUMBER'; field: string; message?: string }
  | { error: 'EMAIL_ALREADY_IN_USE'; message?: string }
  | { error: 'SPECIALIZATION_MISMATCH'; message?: string }
  | { error: 'ONBOARDING_FAILED'; message?: string }
  | { error: 'CLINIC_NOT_FOUND'; message?: string }

function defaultMessageFor(rawBody: unknown): string {
  const body = rawBody as OnboardStaffErrorBody | undefined
  switch (body?.error) {
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FORBIDDEN':
      return 'Only a ClinicAdmin for this clinic can onboard staff here.'
    case 'INVALID_ROLE':
      return 'Only Doctor or Operations roles can be onboarded through this form.'
    case 'MISSING_REQUIRED_FIELD':
      return `Missing required field: ${body.field}.`
    case 'INVALID_MOBILE_NUMBER':
      return 'Mobile number must be a valid Indian number.'
    case 'EMAIL_ALREADY_IN_USE':
      return 'This email is already in use by another staff account.'
    case 'SPECIALIZATION_MISMATCH':
      return 'This license number is already on file under a different specialization. Please double-check it.'
    case 'ONBOARDING_FAILED':
      return 'Onboarding failed. Please try again.'
    case 'CLINIC_NOT_FOUND':
      return 'This clinic could not be found.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export interface DeactivateStaffResponse {
  accountId: string
  clinicId: string
  role: StaffRole | 'ClinicAdmin'
  active: false
}

export type DeactivationReason = 'RESIGNED' | 'SERVICE_NOT_REQUIRED'

export type DeactivateStaffErrorBody =
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'NOT_FOUND'; message?: string }
  | { error: 'LAST_ACTIVE_CLINIC_ADMIN'; message?: string }
  | { error: 'MISSING_REASON'; message?: string }
  | { error: 'INVALID_REASON'; message?: string }

export class DeactivateStaffApiError extends Error {
  readonly body: DeactivateStaffErrorBody

  constructor(body: DeactivateStaffErrorBody) {
    super(defaultDeactivateMessageFor(body) ?? body.message)
    this.name = 'DeactivateStaffApiError'
    this.body = body
  }
}

function defaultDeactivateMessageFor(body: DeactivateStaffErrorBody): string {
  switch (body.error) {
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FORBIDDEN':
      return 'Only a ClinicAdmin for this clinic can deactivate staff here.'
    case 'NOT_FOUND':
      return 'This staff member could not be found at this clinic.'
    case 'LAST_ACTIVE_CLINIC_ADMIN':
      return 'This is the clinic’s only active ClinicAdmin and cannot be deactivated — a clinic can never be left without an administrator.'
    case 'MISSING_REASON':
      return 'Please select a reason for deactivation.'
    case 'INVALID_REASON':
      return 'Please select a valid reason for deactivation.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

// See specs/005-last-active-clinicadmin-protection/contracts/staff-deactivation.md
export async function deactivateStaff(
  clinicId: string,
  accountId: string,
  reason: DeactivationReason,
  token: string,
): Promise<DeactivateStaffResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/staff/${accountId}/deactivate`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ reason }),
    },
  )

  if (!response.ok) {
    let body: DeactivateStaffErrorBody
    try {
      body = (await response.json()) as DeactivateStaffErrorBody
    } catch {
      body = { error: 'FORBIDDEN' }
    }
    throw new DeactivateStaffApiError(body)
  }

  return (await response.json()) as DeactivateStaffResponse
}

export async function onboardStaff(
  clinicId: string,
  payload: OnboardStaffRequest,
  token: string,
): Promise<OnboardStaffResponse> {
  return apiRequest<OnboardStaffResponse>(`/api/v1/clinics/${clinicId}/staff`, {
    method: 'POST',
    token,
    body: payload,
    fallbackMessage: defaultMessageFor,
  })
}

// real-bug-fix 2026-09-17: mirrors clinic-verification/api.ts's resetClinicAdminPassword/
// setClinicAdminPassword pair exactly, scoped instead to a ClinicAdmin resetting a Doctor or
// Operations staff member's login at their own clinic - see StaffPasswordResetService's own
// Javadoc for why a fellow ClinicAdmin target is rejected (no override).
export interface ResetStaffPasswordResult {
  accountId: string
  email: string
  staffCode: string
  temporaryPassword: string
}

export type ResetStaffPasswordErrorBody =
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'NOT_FOUND'; message?: string }
  | { error: 'INVALID_PASSWORD'; failedRules?: string[]; message?: string }

export class ResetStaffPasswordApiError extends Error {
  readonly body: ResetStaffPasswordErrorBody

  constructor(body: ResetStaffPasswordErrorBody) {
    super(defaultResetPasswordMessageFor(body) ?? body.message)
    this.name = 'ResetStaffPasswordApiError'
    this.body = body
  }
}

function defaultResetPasswordMessageFor(body: ResetStaffPasswordErrorBody): string {
  switch (body.error) {
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FORBIDDEN':
      return 'Only a ClinicAdmin for this clinic can reset a staff member’s password here, and not for a fellow ClinicAdmin.'
    case 'NOT_FOUND':
      return 'This staff member could not be found at this clinic.'
    case 'INVALID_PASSWORD':
      return body.failedRules?.length ? body.failedRules.join(' ') : 'Password does not satisfy the required policy.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

async function parseResetPasswordError(response: Response): Promise<ResetStaffPasswordApiError> {
  let body: ResetStaffPasswordErrorBody
  try {
    body = (await response.json()) as ResetStaffPasswordErrorBody
  } catch {
    body = { error: 'FORBIDDEN' }
  }
  return new ResetStaffPasswordApiError(body)
}

export async function resetStaffPassword(
  clinicId: string,
  accountId: string,
  token: string,
): Promise<ResetStaffPasswordResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/staff/${accountId}/reset-password`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw await parseResetPasswordError(response)
  }
  return (await response.json()) as ResetStaffPasswordResult
}

export async function setStaffPassword(
  clinicId: string,
  accountId: string,
  newPassword: string,
  token: string,
): Promise<ResetStaffPasswordResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/staff/${accountId}/set-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ newPassword }),
  })
  if (!response.ok) {
    throw await parseResetPasswordError(response)
  }
  return (await response.json()) as ResetStaffPasswordResult
}
