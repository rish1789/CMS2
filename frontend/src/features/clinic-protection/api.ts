// Client for /api/v1/clinics/{clinicId}/protection/flags and .../protection/limit-override
// See specs/060-booking-abuse-prevention/contracts/booking-protection.md #2, #3

import { apiRequest, ApiError } from '../../lib/apiClient'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type SignalType =
  | 'HIGH_ATTEMPT_VOLUME'
  | 'REPEATED_CANCELLATIONS'
  | 'REPEATED_NO_SHOWS'
  | 'OVERLAPPING_APPOINTMENTS'
  | 'REPEATED_RATE_LIMIT_VIOLATIONS'

export type FlagStatus = 'OUTSTANDING' | 'RESOLVED'

export interface ProtectionFlag {
  id: string
  patientAccountId: string
  patientDisplayName: string
  signalType: SignalType
  reason: string
  detectedAt: string
  status: FlagStatus
  resolvedAt: string | null
  resolvedBy: string | null
}

export interface FlagListResponse {
  flags: ProtectionFlag[]
  page: number
  pageSize: number
  totalCount: number
}

export interface BookingSummary {
  bookingId: string
  sessionDate: string
  doctorName: string
  status: string
}

export interface RateLimitViolation {
  occurredAt: string
}

export interface RecentActivity {
  recentBookings: BookingSummary[]
  recentCancellations: BookingSummary[]
  recentNoShows: BookingSummary[]
  rateLimitViolations: RateLimitViolation[]
  globalActiveAppointmentCount: number
  atGlobalLimit: boolean
}

export interface FlagDetailResponse {
  flag: ProtectionFlag
  recentActivity: RecentActivity
}

export interface ClinicLimitOverride {
  maxActiveAppointments: number | null
  globalMax: number
}

export interface ClinicLimitOverrideHistoryEntry {
  previousMaxActiveAppointments: number | null
  newMaxActiveAppointments: number | null
  changedAt: string
  changedBy: string
}

export type ClinicProtectionErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FLAG_NOT_FOUND'; message?: string }
  | { error: 'FLAG_ALREADY_RESOLVED'; message?: string }
  | { error: 'CLINIC_LIMIT_EXCEEDS_GLOBAL_CAP'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as ClinicProtectionErrorBody | undefined)?.error
  switch (error) {
    case 'FORBIDDEN':
      return 'Only a ClinicAdmin at this clinic can review booking protection.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FLAG_NOT_FOUND':
      return 'This flag could not be found.'
    case 'FLAG_ALREADY_RESOLVED':
      return 'This flag has already been resolved.'
    case 'CLINIC_LIMIT_EXCEEDS_GLOBAL_CAP':
      return 'The clinic limit must be a positive number that does not exceed the global limit.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function listFlags(
  clinicId: string,
  token: string,
  options: { status?: FlagStatus; patientAccountId?: string; page?: number; size?: number } = {},
): Promise<FlagListResponse> {
  const params = new URLSearchParams()
  if (options.status) params.set('status', options.status)
  if (options.patientAccountId) params.set('patientAccountId', options.patientAccountId)
  params.set('page', String(options.page ?? 0))
  params.set('size', String(options.size ?? 20))
  return apiRequest<FlagListResponse>(`/api/v1/clinics/${clinicId}/protection/flags?${params.toString()}`, {
    token,
    fallbackMessage: defaultMessageFor,
  })
}

export async function getFlagDetail(clinicId: string, flagId: string, token: string): Promise<FlagDetailResponse> {
  return apiRequest<FlagDetailResponse>(`/api/v1/clinics/${clinicId}/protection/flags/${flagId}`, {
    token,
    fallbackMessage: defaultMessageFor,
  })
}

export async function resolveFlag(clinicId: string, flagId: string, token: string): Promise<ProtectionFlag> {
  return apiRequest<ProtectionFlag>(`/api/v1/clinics/${clinicId}/protection/flags/${flagId}/resolve`, {
    method: 'POST',
    token,
    fallbackMessage: defaultMessageFor,
  })
}

export async function getLimitOverride(clinicId: string, token: string): Promise<ClinicLimitOverride> {
  return apiRequest<ClinicLimitOverride>(`/api/v1/clinics/${clinicId}/protection/limit-override`, {
    token,
    fallbackMessage: defaultMessageFor,
  })
}

export async function setLimitOverride(
  clinicId: string,
  maxActiveAppointments: number,
  token: string,
): Promise<ClinicLimitOverride> {
  return apiRequest<ClinicLimitOverride>(`/api/v1/clinics/${clinicId}/protection/limit-override`, {
    method: 'PUT',
    token,
    body: { maxActiveAppointments },
    fallbackMessage: defaultMessageFor,
  })
}

// A plain fetch, not apiRequest - this endpoint's success response is a bodyless 200 OK
// (ClinicBookingLimitOverrideController.delete is void, matching the codebase's existing
// void-DELETE convention - see scheduling/api.ts's deleteSchedule for the same reason), and
// apiRequest's response.json() on a success path would throw on an empty body.
export async function deleteLimitOverride(clinicId: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/protection/limit-override`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      body = undefined
    }
    throw new ApiError(response.status, defaultMessageFor(body), body)
  }
}

export async function getLimitOverrideHistory(
  clinicId: string,
  token: string,
): Promise<ClinicLimitOverrideHistoryEntry[]> {
  return apiRequest<ClinicLimitOverrideHistoryEntry[]>(
    `/api/v1/clinics/${clinicId}/protection/limit-override/history`,
    { token, fallbackMessage: defaultMessageFor },
  )
}
