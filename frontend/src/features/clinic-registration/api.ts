// Client for POST /api/v1/clinics/register — see specs/001-clinic-registration/contracts/register-clinic.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface RegisterClinicRequest {
  clinic: {
    name: string
    address: string
    city?: string
    contactEmail?: string
    contactMobile?: string
  }
  admin: {
    name: string
    email: string
    password: string
    mobile?: string
  }
}

export interface RegisterClinicResponse {
  clinicId: string
  clinicName: string
  verified: boolean
  admin: {
    accountId: string
    email: string
    staffCode: string
  }
}

export type RegisterClinicErrorBody =
  | { error: 'INVALID_PASSWORD'; message: string; failedRules: string[] }
  | { error: 'INVALID_MOBILE_NUMBER'; field: 'clinic.contactMobile' | 'admin.mobile'; message: string }
  | { error: 'EMAIL_ALREADY_IN_USE'; message: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field: string; message?: string }
  | { error: 'REGISTRATION_FAILED'; message?: string }

export class RegisterClinicApiError extends Error {
  readonly body: RegisterClinicErrorBody

  constructor(body: RegisterClinicErrorBody) {
    super(body.message ?? body.error)
    this.name = 'RegisterClinicApiError'
    this.body = body
  }
}

export async function registerClinic(
  payload: RegisterClinicRequest,
): Promise<RegisterClinicResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let body: RegisterClinicErrorBody
    try {
      body = (await response.json()) as RegisterClinicErrorBody
    } catch {
      body = { error: 'REGISTRATION_FAILED' }
    }
    throw new RegisterClinicApiError(body)
  }

  return (await response.json()) as RegisterClinicResponse
}
