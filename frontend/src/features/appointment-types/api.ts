// Client for POST/GET /api/v1/doctors/{doctorProfileId}/appointment-types
// and PUT /api/v1/doctors/{doctorProfileId}/default-fee
// See specs/017-fee-resolution-locking/contracts
//
// _diagnostics [HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]: no frontend surface of any kind
// existed for these endpoints - three separate booking forms fell back to raw free-text UUID
// inputs for appointment type selection as a direct consequence.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface AppointmentTypeResponse {
  id: string
  doctorProfileId: string
  name: string
  feeOverride: number | null
}

export interface CreateAppointmentTypeRequest {
  name: string
  feeOverride?: number
}

export interface SetDefaultFeeResponse {
  doctorProfileId: string
  amount: number
}

export type AppointmentTypeErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'DOCTOR_PROFILE_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class AppointmentTypeApiError extends Error {
  readonly body: AppointmentTypeErrorBody

  constructor(body: AppointmentTypeErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'AppointmentTypeApiError'
    this.body = body
  }
}

function defaultMessageFor(body: AppointmentTypeErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only this doctor, or a ClinicAdmin at a clinic they are staffed at, can manage appointment types.'
    case 'DOCTOR_PROFILE_NOT_FOUND':
      return 'This doctor could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

async function parseError(response: Response, fallback: AppointmentTypeErrorBody['error']): Promise<never> {
  let body: AppointmentTypeErrorBody
  try {
    body = (await response.json()) as AppointmentTypeErrorBody
  } catch {
    body = { error: fallback }
  }
  throw new AppointmentTypeApiError(body)
}

export async function listAppointmentTypes(
  doctorProfileId: string,
  token: string,
): Promise<AppointmentTypeResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/doctors/${doctorProfileId}/appointment-types`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) await parseError(response, 'DOCTOR_PROFILE_NOT_FOUND')
  return (await response.json()) as AppointmentTypeResponse[]
}

// Patient-facing analog of listAppointmentTypes, backing the picker that replaces the raw
// "Appointment Type ID" text field on ClaimOfferCard - GET
// /api/v1/patients/doctors/{doctorProfileId}/appointment-types, authenticated with a Patient
// Account token, with no ownership/role check on the caller (a patient is neither "this doctor"
// nor a ClinicAdmin, so the staff-only listing above always rejects one).
export async function listPatientAppointmentTypes(
  doctorProfileId: string,
  token: string,
): Promise<AppointmentTypeResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/doctors/${doctorProfileId}/appointment-types`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) await parseError(response, 'DOCTOR_PROFILE_NOT_FOUND')
  return (await response.json()) as AppointmentTypeResponse[]
}

export async function createAppointmentType(
  doctorProfileId: string,
  request: CreateAppointmentTypeRequest,
  token: string,
): Promise<AppointmentTypeResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/doctors/${doctorProfileId}/appointment-types`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) await parseError(response, 'DOCTOR_PROFILE_NOT_FOUND')
  return (await response.json()) as AppointmentTypeResponse
}

export async function setDefaultFee(
  doctorProfileId: string,
  amount: number,
  token: string,
): Promise<SetDefaultFeeResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/doctors/${doctorProfileId}/default-fee`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ amount }),
  })
  if (!response.ok) await parseError(response, 'DOCTOR_PROFILE_NOT_FOUND')
  return (await response.json()) as SetDefaultFeeResponse
}
