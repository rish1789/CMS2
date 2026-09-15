// Client for POST /api/v1/clinics/{clinicId}/slots/{slotId}/complete
// and GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay
// See specs/026-session-delay-tracking/contracts/session-delay-tracking.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SlotCompletionResponse {
  slotId: string
  status: 'COMPLETED'
}

export interface SessionDelayResponse {
  sessionId: string
  applicable: boolean
  delayMinutes: number | null
}

export type SlotCompletionErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SLOT_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_FIXED_TIME_SESSION'; message?: string }
  | { error: 'SLOT_NOT_COMPLETABLE'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export type SessionDelayErrorBody =
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class SlotCompletionApiError extends Error {
  readonly body: SlotCompletionErrorBody

  constructor(body: SlotCompletionErrorBody) {
    super(defaultCompletionMessageFor(body) ?? body.message)
    this.name = 'SlotCompletionApiError'
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
