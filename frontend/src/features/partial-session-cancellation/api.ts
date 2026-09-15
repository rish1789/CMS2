// Client for POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff
// See specs/030-partial-cutoff-session-cancellation/contracts/partial-session-cancellation.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SessionCancellationResponse {
  sessionId: string
  bookingsCancelled: number
}

export type PartialCancellationErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'INVALID_CANCELLATION_RANGE'; message?: string }

export class PartialCancellationApiError extends Error {
  readonly body: PartialCancellationErrorBody

  constructor(body: PartialCancellationErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'PartialCancellationApiError'
    this.body = body
  }
}

function defaultMessageFor(body: PartialCancellationErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can cancel this session.'
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'INVALID_CANCELLATION_RANGE':
      return 'The end time must be after the start time.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function cancelFromCutoff(
  clinicId: string,
  sessionId: string,
  cutoffTime: string,
  toTime: string,
  token: string,
): Promise<SessionCancellationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/cancel-from-cutoff`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ cutoffTime, toTime }),
  })

  if (!response.ok) {
    let body: PartialCancellationErrorBody
    try {
      body = (await response.json()) as PartialCancellationErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new PartialCancellationApiError(body)
  }

  return (await response.json()) as SessionCancellationResponse
}
