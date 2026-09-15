// Client for GET /api/v1/discovery/search, GET /api/v1/discovery/cities,
// and GET /api/v1/discovery/specializations
// See specs/010-public-discovery-search/contracts/discovery-search.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface DiscoveryResult {
  doctorProfileId: string
  doctorName: string
  specialization: string
  experienceYears: number
  clinicId: string
  clinicName: string
  clinicAddress: string
  clinicCity: string | null
}

export type DiscoverySortField = 'doctorName' | 'clinicName' | 'specialization' | 'experienceYears'
export type SortDirection = 'asc' | 'desc'

export interface SearchDiscoveryParams {
  q?: string
  city?: string
  specialization?: string
  minExperienceYears?: number
  sort?: DiscoverySortField
  direction?: SortDirection
}

export async function searchDiscovery(params: SearchDiscoveryParams = {}): Promise<DiscoveryResult[]> {
  const url = new URL(`${API_BASE_URL}/api/v1/discovery/search`)
  if (params.q && params.q.trim() !== '') url.searchParams.set('q', params.q.trim())
  if (params.city) url.searchParams.set('city', params.city)
  if (params.specialization) url.searchParams.set('specialization', params.specialization)
  if (params.minExperienceYears !== undefined) {
    url.searchParams.set('minExperienceYears', String(params.minExperienceYears))
  }
  if (params.sort) url.searchParams.set('sort', params.sort)
  if (params.direction) url.searchParams.set('direction', params.direction)

  const response = await fetch(url.toString())

  if (!response.ok) {
    throw new Error('Discovery search failed. Please try again.')
  }

  return (await response.json()) as DiscoveryResult[]
}

// Drives the City filter dropdown - only cities with at least one eligible doctor right now.
export async function listDiscoveryCities(): Promise<string[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/discovery/cities`)
  if (!response.ok) {
    throw new Error('Could not load the city list.')
  }
  return (await response.json()) as string[]
}

// Drives the Specialization filter dropdown - same eligibility gate as the cities list.
export async function listDiscoverySpecializations(): Promise<string[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/discovery/specializations`)
  if (!response.ok) {
    throw new Error('Could not load the specialization list.')
  }
  return (await response.json()) as string[]
}
