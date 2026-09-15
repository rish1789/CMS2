// Client for GET /api/v1/patients/clinics ("My clinics")

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PatientClinic {
  clinicId: string
  name: string
  address: string
}

export interface PatientClinicListResult {
  clinics: PatientClinic[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListMyClinicsParams {
  page?: number
  size?: number
}

export async function listMyClinics(token: string, params: ListMyClinicsParams = {}): Promise<PatientClinicListResult> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(`${API_BASE_URL}/api/v1/patients/clinics${queryString ? `?${queryString}` : ''}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load your clinics.')
  }

  return (await response.json()) as PatientClinicListResult
}
