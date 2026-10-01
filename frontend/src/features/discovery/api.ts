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
  // 072-discovery-pagination: 0-indexed, like the server; the server caps size at 50.
  page?: number
  size?: number
}

export interface DiscoveryPage {
  results: DiscoveryResult[]
  // Matches across all pages, from the X-Total-Count response header.
  totalCount: number
}

export async function searchDiscovery(params: SearchDiscoveryParams = {}): Promise<DiscoveryPage> {
  const url = new URL(`${API_BASE_URL}/api/v1/discovery/search`)
  if (params.q && params.q.trim() !== '') url.searchParams.set('q', params.q.trim())
  if (params.city) url.searchParams.set('city', params.city)
  if (params.specialization) url.searchParams.set('specialization', params.specialization)
  if (params.minExperienceYears !== undefined) {
    url.searchParams.set('minExperienceYears', String(params.minExperienceYears))
  }
  if (params.sort) url.searchParams.set('sort', params.sort)
  if (params.direction) url.searchParams.set('direction', params.direction)
  if (params.page !== undefined) url.searchParams.set('page', String(params.page))
  if (params.size !== undefined) url.searchParams.set('size', String(params.size))

  const response = await fetch(url.toString())

  if (!response.ok) {
    throw new Error('Discovery search failed. Please try again.')
  }

  const results = (await response.json()) as DiscoveryResult[]
  const header = Number.parseInt(response.headers.get('X-Total-Count') ?? '', 10)
  // Without a readable total, count only what has been seen, so no page past it is offered.
  const seen = (params.page ?? 0) * (params.size ?? results.length) + results.length
  return { results, totalCount: Number.isFinite(header) && header >= 0 ? header : seen }
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
