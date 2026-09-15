// Client for GET /api/v1/clinics/{clinicId}/doctors
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface DoctorSummary {
  doctorProfileId: string
  name: string
  staffCode: string
  specialization: string
}

export interface DoctorListResult {
  doctors: DoctorSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListClinicDoctorsParams {
  q?: string
  page?: number
  size?: number
}

export interface PatientDoctorSummary {
  doctorProfileId: string
  name: string
  specialization: string
  experienceYears: number
}

export interface PatientDoctorListResult {
  doctors: PatientDoctorSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export class DoctorPickerApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'DoctorPickerApiError'
    this.status = status
  }
}

// pagination-unification-2026-09-10: paginated server-side - a clinic with a large doctor
// roster shouldn't force this picker to fetch every doctor in one response.
export async function listClinicDoctors(
  clinicId: string,
  token: string,
  params: ListClinicDoctorsParams = {},
): Promise<DoctorListResult> {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!response.ok) {
    throw new DoctorPickerApiError(response.status)
  }
  return (await response.json()) as DoctorListResult
}

// Patient-facing analog of listClinicDoctors, backing the doctor picker that replaces the raw
// doctorProfileId text field on JoinWaitlistForm - GET /api/v1/patients/clinics/{clinicId}/doctors,
// authenticated with a Patient Account token rather than a staff one, and no clinic-membership
// check on the caller (a patient is never staffed anywhere).
export async function listPatientClinicDoctors(
  clinicId: string,
  token: string,
  params: ListClinicDoctorsParams = {},
): Promise<PatientDoctorListResult> {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(
    `${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/doctors${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!response.ok) {
    throw new DoctorPickerApiError(response.status)
  }
  return (await response.json()) as PatientDoctorListResult
}
