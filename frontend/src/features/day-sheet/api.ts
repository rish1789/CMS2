// Client for GET /api/v1/clinics/{clinicId}/sessions and .../sessions/{sessionId}/day-sheet
// See specs/041-staff-console-pickers/contracts/staff-console-pickers.md

import type { VisitReason } from '../front-desk-walk-in/visitReasons'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SessionSummary {
  sessionId: string
  doctorProfileId: string
  doctorName: string
  sessionDate: string
  startTime: string
  endTime: string
  mode: 'FIXED_TIME' | 'QUEUE'
  bookedSlotCount: number
  totalSlotCount: number
  // 063-front-desk-walk-in: walk-ins waiting in a Fixed-Time session's walk-in line, and whether
  // anyone is in with the doctor right now (the Doctor free/busy hint). 0/false for Queue sessions.
  walkInsWaiting: number
  inWithDoctor: boolean
  // 065-phase1-stabilization: the whole session has been cancelled - it takes no bookings.
  cancelled: boolean
}

export interface DoctorSummary {
  doctorProfileId: string
  name: string
  staffCode: string
}

export interface SessionListResult {
  sessions: SessionSummary[]
  doctors: DoctorSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListSessionsParams {
  page?: number
  size?: number
  doctorProfileId?: string
  // dashboard-live-data-2026-09-10: ISO dates (YYYY-MM-DD), narrowing the default 14-day window -
  // the ClinicToolsDashboard's "today's session count" tile uses from=to=today with size=1, just
  // to read totalCount, without fetching the day's actual session rows.
  from?: string
  to?: string
}

export interface BookingDetail {
  bookingId: string
  patientId: string
  patientName: string
  isWalkIn: boolean
  // 063-front-desk-walk-in: why a walk-in came in; null for booked visits.
  visitReason: VisitReason | null
  visitReasonDetail: string | null
}

export interface SlotDetail {
  slotId: string
  startTime: string | null
  endTime: string | null
  tokenNumber: number | null
  status: 'OPEN' | 'BOOKED' | 'APPEARED' | 'COMPLETED' | 'NO_SHOW'
  // 063-front-desk-walk-in: when the patient was sent in, and when the visit finished.
  appearedAt: string | null
  completedAt: string | null
  booking: BookingDetail | null
}

// 065-phase1-stabilization: a cancelled [fromTime, toTime) part of a session; a null toTime
// means to the end of the session.
export interface CancelledRange {
  fromTime: string
  toTime: string | null
}

export interface SessionDaySheet {
  sessionId: string
  doctorProfileId: string
  doctorName: string
  sessionDate: string
  mode: 'FIXED_TIME' | 'QUEUE'
  slots: SlotDetail[]
  // 065-phase1-stabilization: the whole session is cancelled, and any cancelled ranges within it.
  cancelled: boolean
  cancelledRanges: CancelledRange[]
}

export interface TodaySessionStats {
  completedCount: number
  noShowCount: number
}

export class DaySheetApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'DaySheetApiError'
    this.status = status
  }
}

async function throwIfNotOk(response: Response): Promise<void> {
  if (!response.ok) {
    throw new DaySheetApiError(response.status)
  }
}

export async function listSessions(
  clinicId: string,
  token: string,
  params: ListSessionsParams = {},
): Promise<SessionListResult> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.doctorProfileId) query.set('doctorProfileId', params.doctorProfileId)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  const queryString = query.toString()

  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  await throwIfNotOk(response)
  return (await response.json()) as SessionListResult
}

export async function getDaySheet(clinicId: string, sessionId: string, token: string): Promise<SessionDaySheet> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/day-sheet`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as SessionDaySheet
}

// 051-staff-dashboard-enhancement T010: today's completed/no-show counts for the dashboard's
// "Today's stats" tile (contracts/today-session-stats.md).
export async function getTodayStats(clinicId: string, token: string): Promise<TodaySessionStats> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/today-stats`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as TodaySessionStats
}
