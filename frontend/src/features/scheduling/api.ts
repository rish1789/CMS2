// Client for POST/GET /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules
// See specs/013-recurring-schedule-definition/contracts/schedule.md
// 054-forms-validation-consistency: migrated onto the shared apiClient (043's own fix,
// applied here for the first time) - this file had the identical defaultMessageFor(body) ??
// body.message dead-code bug 043 found and fixed elsewhere, independently discarding the
// backend's real message. `ScheduleApiError` is gone; callers catch the shared `ApiError`.

import { apiRequest, ApiError } from '../../lib/apiClient'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type ScheduleMode = 'FIXED_TIME' | 'QUEUE'

export interface CreateScheduleRequest {
  daysOfWeek: string[]
  startTime: string
  endTime: string
  mode: ScheduleMode
  slotIntervalMinutes?: number
  // 055-schedule-break-window: both set, or both omitted.
  breakStartTime?: string
  breakEndTime?: string
}

export interface ScheduleResponse {
  id: string
  clinicId: string
  doctorProfileId: string
  daysOfWeek: string[]
  startTime: string
  endTime: string
  mode: ScheduleMode
  slotIntervalMinutes: number | null
  breakStartTime?: string | null
  breakEndTime?: string | null
}

export type ScheduleApiErrorBody =
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'CLINIC_NOT_FOUND'; message?: string }
  | { error: 'DOCTOR_PROFILE_NOT_FOUND'; message?: string }
  | { error: 'DOCTOR_NOT_STAFFED_AT_CLINIC'; message?: string }
  | { error: 'INVALID_SCHEDULE'; message?: string }
  | { error: 'SCHEDULE_OVERLAP'; message?: string }
  | { error: 'SCHEDULE_NOT_FOUND'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as ScheduleApiErrorBody | undefined)?.error
  switch (error) {
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FORBIDDEN':
      return 'Only this clinic’s ClinicAdmin, or the doctor themselves, can manage this schedule.'
    case 'CLINIC_NOT_FOUND':
      return 'This clinic could not be found.'
    case 'DOCTOR_PROFILE_NOT_FOUND':
      return 'This doctor could not be found.'
    case 'DOCTOR_NOT_STAFFED_AT_CLINIC':
      return 'This doctor is not actively assigned to this clinic.'
    case 'INVALID_SCHEDULE':
      return 'Please check the days, time range, and slot interval.'
    case 'SCHEDULE_OVERLAP':
      return 'This overlaps another schedule this doctor already has, at this clinic or another one.'
    case 'SCHEDULE_NOT_FOUND':
      return 'This schedule could not be found.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function createSchedule(
  clinicId: string,
  doctorProfileId: string,
  payload: CreateScheduleRequest,
  token: string,
): Promise<ScheduleResponse> {
  return apiRequest<ScheduleResponse>(`/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules`, {
    method: 'POST',
    token,
    body: payload,
    fallbackMessage: defaultMessageFor,
  })
}

export async function editSchedule(
  clinicId: string,
  doctorProfileId: string,
  scheduleId: string,
  payload: CreateScheduleRequest,
  token: string,
): Promise<ScheduleResponse> {
  return apiRequest<ScheduleResponse>(
    `/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules/${scheduleId}`,
    {
      method: 'PATCH',
      token,
      body: payload,
      fallbackMessage: defaultMessageFor,
    },
  )
}

export async function listSchedules(
  clinicId: string,
  doctorProfileId: string,
  token: string,
): Promise<ScheduleResponse[]> {
  return apiRequest<ScheduleResponse[]>(`/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules`, {
    token,
    fallbackMessage: defaultMessageFor,
  })
}

// 055-schedule-break-window: a plain fetch, not apiRequest - this endpoint's success response
// is a bodyless 200 OK (ScheduleDeletionController is void, matching the codebase's existing
// void-DELETE convention), and apiRequest's response.json() on a success path would throw on
// an empty body. Mirrors session-cancellation/api.ts's deleteSession for the same reason.
export async function deleteSchedule(
  clinicId: string,
  doctorProfileId: string,
  scheduleId: string,
  token: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules/${scheduleId}`,
    {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    },
  )

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
