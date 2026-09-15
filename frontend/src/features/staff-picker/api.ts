// Client for GET /api/v1/clinics/{clinicId}/staff
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface StaffSummary {
  roleAssignmentId: string
  accountId: string
  name: string
  staffCode: string
  role: 'ClinicAdmin' | 'Doctor' | 'Operations'
  email: string
  mobile: string | null
  // Doctor-only - null for ClinicAdmin/Operations, which have no DoctorProfile.
  specialization: string | null
  experienceYears: number | null
  joinedAt: string
  active: boolean
}

export interface StaffListResult {
  staff: StaffSummary[]
  page: number
  pageSize: number
  totalCount: number
  // Always the complete set of specializations among this clinic's Doctors, independent of the
  // current page/filters - mirrors day-sheet/api.ts's SessionListResult.doctors.
  specializations: string[]
}

export interface ListClinicStaffParams {
  q?: string
  role?: StaffSummary['role']
  active?: boolean
  specialization?: string
  sortBy?: 'name' | 'experienceYears' | 'joinedAt'
  sortDir?: 'asc' | 'desc'
  page?: number
  size?: number
}

export class StaffPickerApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'StaffPickerApiError'
    this.status = status
  }
}

// pagination-unification-2026-09-10: search/role/status/specialization filtering, sorting, and
// paging all now happen server-side - previously this fetched the clinic's entire roster in one
// call and did all of it in memory, behind a UI that already looked server-paginated.
export async function listClinicStaff(
  clinicId: string,
  token: string,
  params: ListClinicStaffParams = {},
): Promise<StaffListResult> {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.role) query.set('role', params.role)
  if (params.active !== undefined) query.set('active', String(params.active))
  if (params.specialization) query.set('specialization', params.specialization)
  if (params.sortBy) query.set('sortBy', params.sortBy)
  if (params.sortDir) query.set('sortDir', params.sortDir)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/staff${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!response.ok) {
    throw new StaffPickerApiError(response.status)
  }
  return (await response.json()) as StaffListResult
}
