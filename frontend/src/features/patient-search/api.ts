// Client for GET /api/v1/clinics/{clinicId}/patients/search?q=
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PatientSearchResult {
  patientId: string
  name: string
  phone: string | null
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
