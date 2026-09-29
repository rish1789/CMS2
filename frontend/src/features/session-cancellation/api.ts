// Client for POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel
// See specs/029-whole-day-session-cancellation/contracts/session-cancellation.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SessionCancellationResponse {
  sessionId: string
  bookingsCancelled: number
}

export type SessionCancellationErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'SESSION_ALREADY_CANCELLED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class SessionCancellationApiError extends Error {
  readonly body: SessionCancellationErrorBody

  constructor(body: SessionCancellationErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'SessionCancellationApiError'
    this.body = body
  }
}

function defaultMessageFor(body: SessionCancellationErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can cancel this session.'
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'SESSION_ALREADY_CANCELLED':
      return 'This session is already cancelled.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function cancelSession(
  clinicId: string,
  sessionId: string,
  token: string,
): Promise<SessionCancellationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/cancel`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SessionCancellationErrorBody
    try {
      body = (await response.json()) as SessionCancellationErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new SessionCancellationApiError(body)
  }

  return (await response.json()) as SessionCancellationResponse
}

// real-bug-fix 2026-09-17: distinct from cancelSession above - that only cancels Bookings inside
// a Session, it never removes the Session/Slot rows. This is the intended way to remove a Session
// generated with wrong values from a since-corrected Schedule (Schedule edits are deliberately
// non-retroactive) - see specs/029-whole-day-session-cancellation/contracts/session-deletion.md.
export type SessionDeletionErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'SESSION_DELETION_BLOCKED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class SessionDeletionApiError extends Error {
  readonly body: SessionDeletionErrorBody

  constructor(body: SessionDeletionErrorBody) {
    super(defaultDeletionMessageFor(body) ?? body.message)
    this.name = 'SessionDeletionApiError'
    this.body = body
  }
}

function defaultDeletionMessageFor(body: SessionDeletionErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can delete this session.'
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'SESSION_DELETION_BLOCKED':
      return 'This session has bookings, waitlist offers or a cancellation on record, so it cannot be deleted.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function deleteSession(clinicId: string, sessionId: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: SessionDeletionErrorBody
    try {
      body = (await response.json()) as SessionDeletionErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new SessionDeletionApiError(body)
  }
}
