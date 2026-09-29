// Client for POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff
// See specs/030-partial-cutoff-session-cancellation/contracts/partial-session-cancellation.md
// 046-frontend-api-client: migrated onto the shared apiClient (see its own ApiError export).

import { apiRequest } from '../../lib/apiClient'

export interface SessionCancellationResponse {
  sessionId: string
  bookingsCancelled: number
}

export type PartialCancellationErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'INVALID_CANCELLATION_RANGE'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as PartialCancellationErrorBody | undefined)?.error
  switch (error) {
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
  return apiRequest<SessionCancellationResponse>(
    `/api/v1/clinics/${clinicId}/sessions/${sessionId}/cancel-from-cutoff`,
    { method: 'POST', token, body: { cutoffTime, toTime }, fallbackMessage: defaultMessageFor },
  )
}
