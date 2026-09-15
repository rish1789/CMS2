// Client for GET /api/v1/clinics/mine
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface ClinicMembership {
  clinicId: string
  name: string
  address: string
  role: 'ClinicAdmin' | 'Doctor' | 'Operations'
}

export interface MyClinicsListResult {
  clinics: ClinicMembership[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListMyClinicsParams {
  page?: number
  size?: number
}

export class MyClinicsApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'MyClinicsApiError'
    this.status = status
  }
}

// pagination-unification-2026-09-10: paginated server-side - an Account with roles at many
// clinics shouldn't force a single unbounded fetch.
export async function listMyClinics(token: string, params: ListMyClinicsParams = {}): Promise<MyClinicsListResult> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/mine${queryString ? `?${queryString}` : ''}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new MyClinicsApiError(response.status)
  }
  return (await response.json()) as MyClinicsListResult
}
