// Client for POST /api/v1/clinics/{clinicId}/slots/{slotId}/complete,
// POST /api/v1/clinics/{clinicId}/slots/{slotId}/appeared,
// and GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay
// See specs/026-session-delay-tracking/contracts/session-delay-tracking.md and
// specs/057-day-sheet-status-overhaul/contracts/day-sheet-status-flow.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SlotCompletionResponse {
  slotId: string
  status: 'COMPLETED'
}

export interface SlotAppearedResponse {
  slotId: string
  status: 'APPEARED'
}

export interface SessionDelayResponse {
  sessionId: string
  applicable: boolean
  delayMinutes: number | null
}

// 061-doctor-live-status: the live, on-demand status - distinct from SessionDelayResponse above
// (the existing cached/trigger-recalculated figure, left unchanged - spec Assumption A6).
export type LiveScheduleStatus = 'NOT_STARTED' | 'ON_TIME' | 'RUNNING_EARLY' | 'DELAYED' | 'COMPLETED'

export interface SessionLiveStatusResponse {
  sessionId: string
  applicable: boolean
  status: LiveScheduleStatus | null
  currentPatientOrdinal: number | null
  expectedPatientOrdinal: number | null
  deviationMinutes: number | null
  firstSlotTime: string | null
  operationalDay: string | null
}

// 061-doctor-live-status (contracts/doctor-live-status.md, FR-004/FR-011): the patient-safe
// shape - plain-language statusText only, never a raw LiveScheduleStatus code, and no other
// patient's data.
export interface PatientLiveStatusResponse {
  bookingId: string
  applicable: boolean
  doctorName: string | null
  currentPatientOrdinal: number | null
  statusText: string | null
  estimatedWaitMinutes: number | null
}

export type SlotCompletionErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SLOT_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_FIXED_TIME_SESSION'; message?: string }
  | { error: 'SLOT_NOT_COMPLETABLE'; message?: string }
  | { error: 'SLOT_NOT_YET_STARTED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export type SessionDelayErrorBody =
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export type SessionLiveStatusErrorBody =
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export type PatientLiveStatusErrorBody =
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

// 057-day-sheet-status-overhaul: ClinicAdmin/Operations only - no doctor branch, unlike
// SlotCompletionErrorBody's FORBIDDEN, which a treating doctor can now also trigger around.
export type SlotAppearedErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SLOT_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_FIXED_TIME_SESSION'; message?: string }
  | { error: 'SLOT_NOT_APPEARABLE'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class SlotCompletionApiError extends Error {
  readonly body: SlotCompletionErrorBody

  constructor(body: SlotCompletionErrorBody) {
    super(defaultCompletionMessageFor(body) ?? body.message)
    this.name = 'SlotCompletionApiError'
    this.body = body
  }
}

export class SlotAppearedApiError extends Error {
  readonly body: SlotAppearedErrorBody

  constructor(body: SlotAppearedErrorBody) {
    super(defaultAppearedMessageFor(body) ?? body.message)
    this.name = 'SlotAppearedApiError'
    this.body = body
  }
}

export class SessionDelayApiError extends Error {
  readonly body: SessionDelayErrorBody

  constructor(body: SessionDelayErrorBody) {
    super(defaultDelayMessageFor(body) ?? body.message)
    this.name = 'SessionDelayApiError'
    this.body = body
  }
}

export class SessionLiveStatusApiError extends Error {
  readonly body: SessionLiveStatusErrorBody

  constructor(body: SessionLiveStatusErrorBody) {
    super(defaultLiveStatusMessageFor(body) ?? body.message)
    this.name = 'SessionLiveStatusApiError'
    this.body = body
  }
}

export class PatientLiveStatusApiError extends Error {
  readonly body: PatientLiveStatusErrorBody

  constructor(body: PatientLiveStatusErrorBody) {
    super(defaultPatientLiveStatusMessageFor(body) ?? body.message)
    this.name = 'PatientLiveStatusApiError'
    this.body = body
  }
}

function defaultCompletionMessageFor(body: SlotCompletionErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can mark this slot completed.'
    case 'SLOT_NOT_FOUND':
      return 'This slot could not be found.'
    case 'NOT_A_FIXED_TIME_SESSION':
      return 'Only fixed-time sessions support marking a slot completed.'
    case 'SLOT_NOT_COMPLETABLE':
      return 'This slot cannot be marked completed — it must be booked and not already completed.'
    case 'SLOT_NOT_YET_STARTED':
      return "This slot can't be marked completed before its scheduled start time."
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

function defaultAppearedMessageFor(body: SlotAppearedErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can mark a slot appeared.'
    case 'SLOT_NOT_FOUND':
      return 'This slot could not be found.'
    case 'NOT_A_FIXED_TIME_SESSION':
      return 'Only fixed-time sessions support marking a slot appeared.'
    case 'SLOT_NOT_APPEARABLE':
      return 'This slot cannot be marked appeared — it must be booked or no-show.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

function defaultDelayMessageFor(body: SessionDelayErrorBody): string {
  switch (body.error) {
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

function defaultLiveStatusMessageFor(body: SessionLiveStatusErrorBody): string {
  switch (body.error) {
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'FORBIDDEN':
      return 'You do not have access to this session.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

function defaultPatientLiveStatusMessageFor(body: PatientLiveStatusErrorBody): string {
  switch (body.error) {
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function completeSlot(clinicId: string, slotId: string, token: string): Promise<SlotCompletionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/slots/${slotId}/complete`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SlotCompletionErrorBody
    try {
      body = (await response.json()) as SlotCompletionErrorBody
    } catch {
      body = { error: 'SLOT_NOT_FOUND' }
    }
    throw new SlotCompletionApiError(body)
  }

  return (await response.json()) as SlotCompletionResponse
}

export async function markSlotAppeared(clinicId: string, slotId: string, token: string): Promise<SlotAppearedResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/slots/${slotId}/appeared`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SlotAppearedErrorBody
    try {
      body = (await response.json()) as SlotAppearedErrorBody
    } catch {
      body = { error: 'SLOT_NOT_FOUND' }
    }
    throw new SlotAppearedApiError(body)
  }

  return (await response.json()) as SlotAppearedResponse
}

export async function getSessionDelay(clinicId: string, sessionId: string, token: string): Promise<SessionDelayResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/delay`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SessionDelayErrorBody
    try {
      body = (await response.json()) as SessionDelayErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new SessionDelayApiError(body)
  }

  return (await response.json()) as SessionDelayResponse
}

export async function getSessionLiveStatusAsStaff(
  clinicId: string,
  sessionId: string,
  token: string,
): Promise<SessionLiveStatusResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/live-status`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SessionLiveStatusErrorBody
    try {
      body = (await response.json()) as SessionLiveStatusErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new SessionLiveStatusApiError(body)
  }

  return (await response.json()) as SessionLiveStatusResponse
}

export async function getSessionLiveStatusAsPatient(bookingId: string, token: string): Promise<PatientLiveStatusResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/live-status`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: PatientLiveStatusErrorBody
    try {
      body = (await response.json()) as PatientLiveStatusErrorBody
    } catch {
      body = { error: 'BOOKING_NOT_FOUND' }
    }
    throw new PatientLiveStatusApiError(body)
  }

  return (await response.json()) as PatientLiveStatusResponse
}
