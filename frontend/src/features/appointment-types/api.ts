// Client for POST/GET /api/v1/doctors/{doctorProfileId}/appointment-types and, since
// 068-per-clinic-fees, the clinic-scoped prices at /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees.
// See specs/017-fee-resolution-locking/contracts and specs/068-per-clinic-fees/contracts
//
// _diagnostics [HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]: no frontend surface of any kind
// existed for these endpoints - three separate booking forms fell back to raw free-text UUID
// inputs for appointment type selection as a direct consequence.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface AppointmentTypeResponse {
  id: string
  doctorProfileId: string
  name: string
  // 068: the effective price at the response's clinic; null when not bookable there, or when
  // the listing has no clinic context (the staff doctor-level listing).
  fee: number | null
}

// 068: a type carries no price of its own - prices are set per clinic (ClinicDoctorFees).
export interface CreateAppointmentTypeRequest {
  name: string
}

export interface ClinicAppointmentTypeFee {
  appointmentTypeId: string
  name: string
  price: number | null
  effectiveFee: number | null
}

export interface ClinicDoctorFees {
  clinicId: string
  doctorProfileId: string
  defaultFee: number | null
  appointmentTypes: ClinicAppointmentTypeFee[]
}

export type AppointmentTypeErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'DOCTOR_PROFILE_NOT_FOUND'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'INVALID_FEE_AMOUNT'; message?: string }

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
      return 'You do not have permission for this. Appointment types are managed by the doctor or their clinic admin; prices at this clinic only by its admin.'
    case 'DOCTOR_PROFILE_NOT_FOUND':
      return 'This doctor could not be found.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'This appointment type could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'INVALID_FEE_AMOUNT':
      return 'Enter an amount of 0 or more, with at most 2 decimals.'
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
// "Appointment Type ID" text field on ClaimOfferCard. 068: clinic-scoped, so each type carries
// its effective fee at that clinic - GET
// /api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types, authenticated
// with a Patient Account token, with no ownership/role check on the caller.
export async function listPatientAppointmentTypes(
  clinicId: string,
  doctorProfileId: string,
  token: string,
): Promise<AppointmentTypeResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/doctors/${doctorProfileId}/appointment-types`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
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

// real-bug-fix 2026-09-17: create/list had no way to correct a mistyped name afterward - PUT is
// always safe here even once a Booking already references this AppointmentType, since it only
// ever changes this row's own name.
export async function renameAppointmentType(
  doctorProfileId: string,
  appointmentTypeId: string,
  request: CreateAppointmentTypeRequest,
  token: string,
): Promise<AppointmentTypeResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/doctors/${doctorProfileId}/appointment-types/${appointmentTypeId}`,
    {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    },
  )
  if (!response.ok) await parseError(response, 'APPOINTMENT_TYPE_NOT_FOUND')
  return (await response.json()) as AppointmentTypeResponse
}

// 068-per-clinic-fees: a doctor's prices at one clinic. Any active staff member of the clinic
// can read them; only that clinic's admin can change them.
function feesUrl(clinicId: string, doctorProfileId: string): string {
  return `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/fees`
}

async function sendFees(url: string, method: 'GET' | 'PUT' | 'DELETE', token: string, amount?: number) {
  const response = await fetch(url, {
    method,
    headers:
      amount === undefined
        ? { Authorization: `Bearer ${token}` }
        : { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: amount === undefined ? undefined : JSON.stringify({ amount }),
  })
  if (!response.ok) await parseError(response, 'FORBIDDEN')
  return (await response.json()) as ClinicDoctorFees
}

export function getClinicFees(clinicId: string, doctorProfileId: string, token: string): Promise<ClinicDoctorFees> {
  return sendFees(feesUrl(clinicId, doctorProfileId), 'GET', token)
}

export function setClinicDefaultFee(
  clinicId: string,
  doctorProfileId: string,
  amount: number,
  token: string,
): Promise<ClinicDoctorFees> {
  return sendFees(`${feesUrl(clinicId, doctorProfileId)}/default`, 'PUT', token, amount)
}

export function setClinicTypePrice(
  clinicId: string,
  doctorProfileId: string,
  appointmentTypeId: string,
  amount: number,
  token: string,
): Promise<ClinicDoctorFees> {
  return sendFees(`${feesUrl(clinicId, doctorProfileId)}/appointment-types/${appointmentTypeId}`, 'PUT', token, amount)
}

export function removeClinicTypePrice(
  clinicId: string,
  doctorProfileId: string,
  appointmentTypeId: string,
  token: string,
): Promise<ClinicDoctorFees> {
  return sendFees(`${feesUrl(clinicId, doctorProfileId)}/appointment-types/${appointmentTypeId}`, 'DELETE', token)
}
