// Client for GET /api/v1/clinics/{clinicId}/patients/search?q=
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PatientSearchResult {
  patientId: string
  name: string
  phone: string | null
  // 052-patient-clinical-hub: previously omitted from this shared response shape entirely -
  // the only way to know a patient was anonymized was the instant right after the anonymize
  // action itself succeeded.
  anonymizedAt: string | null
  // Added 2026-09-16: null for a walk-in-only Patient record never linked to a self-service
  // account. StaffJoinWaitlistForm needs this - only a patient with a real account can be put
  // on the waitlist (they have to log in later to claim/decline the offer, 029).
  patientAccountId: string | null
}

export interface PatientSearchListResult {
  patients: PatientSearchResult[]
  page: number
  pageSize: number
  totalCount: number
}

export interface SearchPatientsParams {
  page?: number
  size?: number
}

export interface PatientBookingSummary {
  bookingId: string
  sessionDate: string
  startTime: string | null
  doctorName: string
  appointmentTypeName: string
  bookingStatus: 'ACTIVE' | 'CANCELLED'
  slotStatus: 'OPEN' | 'BOOKED' | 'COMPLETED' | 'NO_SHOW'
}

export interface PatientBookingHistoryResult {
  bookings: PatientBookingSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListPatientBookingsParams {
  page?: number
  size?: number
}

// real-bug-fix 2026-09-17: the Find a Patient page's default table - every appointment-based
// and walk-in patient with an active Booking today, across every doctor at the clinic.
export interface TodayPatient {
  bookingId: string
  patientId: string
  patientName: string
  patientPhone: string | null
  doctorProfileId: string
  doctorName: string
  mode: 'FIXED_TIME' | 'QUEUE'
  startTime: string | null
  tokenNumber: number | null
  slotStatus: 'OPEN' | 'BOOKED' | 'COMPLETED' | 'NO_SHOW'
  isWalkIn: boolean
}

export class PatientSearchApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'PatientSearchApiError'
    this.status = status
  }
}

// pagination-unification-2026-09-10: paginated server-side - a large clinic's patient panel
// can produce hundreds of hits for a common name/phone prefix.
export async function searchPatients(
  clinicId: string,
  term: string,
  token: string,
  params: SearchPatientsParams = {},
): Promise<PatientSearchListResult> {
  const query = new URLSearchParams({ q: term })
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))

  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/patients/search?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new PatientSearchApiError(response.status)
  }
  return (await response.json()) as PatientSearchListResult
}

// real-bug-fix 2026-09-17: default view on the Find a Patient page, above the name/phone
// search - so front-desk staff sees today's full roster at a glance without typing anything.
export async function getTodayPatients(clinicId: string, token: string): Promise<TodayPatient[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/patients/today`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new PatientSearchApiError(response.status)
  }
  return (await response.json()) as TodayPatient[]
}

// staff-console-audit-2026-09-10 P1: backs the entity-context header on the Anonymize page,
// which previously showed a bare red button with no indication of whose record it acted on.
export async function getPatient(clinicId: string, patientId: string, token: string): Promise<PatientSearchResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/patients/${patientId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new PatientSearchApiError(response.status)
  }
  return (await response.json()) as PatientSearchResult
}

// 052-patient-clinical-hub: the Patient Hub's Bookings/Consultations/Prescriptions/External
// Records sections all read from this one clinic-scoped list (contracts/patient-booking-history.md).
export async function listPatientBookings(
  clinicId: string,
  patientId: string,
  token: string,
  params: ListPatientBookingsParams = {},
): Promise<PatientBookingHistoryResult> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/patients/${patientId}/bookings${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!response.ok) {
    throw new PatientSearchApiError(response.status)
  }
  return (await response.json()) as PatientBookingHistoryResult
}
