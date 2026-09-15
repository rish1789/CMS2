// Client for POST/GET /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules
// See specs/013-recurring-schedule-definition/contracts/schedule.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type ScheduleMode = 'FIXED_TIME' | 'QUEUE'

export interface CreateScheduleRequest {
  daysOfWeek: string[]
  startTime: string
  endTime: string
  mode: ScheduleMode
  slotIntervalMinutes?: number
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

export class ScheduleApiError extends Error {
  readonly body: ScheduleApiErrorBody

  constructor(body: ScheduleApiErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'ScheduleApiError'
    this.body = body
  }
}

function defaultMessageFor(body: ScheduleApiErrorBody): string {
  switch (body.error) {
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
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(payload),
    },
  )

  if (!response.ok) {
    let body: ScheduleApiErrorBody
    try {
      body = (await response.json()) as ScheduleApiErrorBody
    } catch {
      body = { error: 'INVALID_SCHEDULE' }
    }
    throw new ScheduleApiError(body)
  }

  return (await response.json()) as ScheduleResponse
}

export async function editSchedule(
  clinicId: string,
  doctorProfileId: string,
  scheduleId: string,
  payload: CreateScheduleRequest,
  token: string,
): Promise<ScheduleResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules/${scheduleId}`,
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(payload),
    },
  )

  if (!response.ok) {
    let body: ScheduleApiErrorBody
    try {
      body = (await response.json()) as ScheduleApiErrorBody
    } catch {
      body = { error: 'INVALID_SCHEDULE' }
    }
    throw new ScheduleApiError(body)
  }

  return (await response.json()) as ScheduleResponse
}

export async function listSchedules(
  clinicId: string,
  doctorProfileId: string,
  token: string,
): Promise<ScheduleResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/doctors/${doctorProfileId}/schedules`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  )

  if (!response.ok) {
    let body: ScheduleApiErrorBody
    try {
      body = (await response.json()) as ScheduleApiErrorBody
    } catch {
      body = { error: 'FORBIDDEN' }
    }
    throw new ScheduleApiError(body)
  }

  return (await response.json()) as ScheduleResponse[]
}
