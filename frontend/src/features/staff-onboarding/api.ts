// Client for POST /api/v1/clinics/{clinicId}/staff
// See specs/004-staff-onboarding-direct-hire/contracts/staff-onboarding.md
//
// Requires the STAFF-audience bearer token from staff-login (passed explicitly by the
// caller, same "caller owns where credentials live" approach as clinic-verification's
// AdminCredentials parameter).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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
  | { error: 'INVALID_MOBILE_NUMBER'; message?: string }
  | { error: 'EMAIL_ALREADY_IN_USE'; message?: string }
  | { error: 'SPECIALIZATION_MISMATCH'; message?: string }
  | { error: 'ONBOARDING_FAILED'; message?: string }
  | { error: 'CLINIC_NOT_FOUND'; message?: string }

export class OnboardStaffApiError extends Error {
  readonly body: OnboardStaffErrorBody

  constructor(body: OnboardStaffErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'OnboardStaffApiError'
    this.body = body
  }
}

function defaultMessageFor(body: OnboardStaffErrorBody): string {
  switch (body.error) {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/staff`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let body: OnboardStaffErrorBody
    try {
      body = (await response.json()) as OnboardStaffErrorBody
    } catch {
      body = { error: 'ONBOARDING_FAILED' }
    }
    throw new OnboardStaffApiError(body)
  }

  return (await response.json()) as OnboardStaffResponse
}
