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
      return 'This session has nothing left to cancel.'
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
