// Client for POST/GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes
// See specs/034-consultation-note-creation/contracts/consultation-note.md
// 054-forms-validation-consistency: migrated onto the shared apiClient - this file had the
// identical defaultMessageFor(body) ?? body.message dead-code bug 043 found and fixed
// elsewhere. `ConsultationNoteApiError` is gone; callers catch the shared `ApiError`.

import { apiRequest } from '../../lib/apiClient'

export interface ConsultationNoteResponse {
  id: string
  bookingId: string
  doctorProfileId: string
  content: string
  createdAt: string
}

export type ConsultationNoteErrorBody =
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'CONSULTATION_NOTE_ALREADY_EXISTS'; message?: string }
  | { error: 'CONSULTATION_NOTE_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  // _diagnostics [LOW] - [CONSULTATION_NOTE] - [ERROR_CODE_OVERGENERALIZATION]: a distinct
  // sentinel for "the error body itself failed to parse as JSON" - never conflated with the real
  // CONSULTATION_NOTE_NOT_FOUND code, so an infra failure (empty body, HTML from a proxy, an
  // unhandled 500) is never mistaken for "no note yet".
  | { error: 'UNKNOWN'; message?: string }

function defaultMessageFor(rawBody: unknown): string {
  const body = rawBody as ConsultationNoteErrorBody | undefined
  switch (body?.error) {
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'FORBIDDEN':
      return 'Only the treating doctor may write or view this note.'
    case 'CONSULTATION_NOTE_ALREADY_EXISTS':
      return 'A consultation note already exists for this booking.'
    case 'CONSULTATION_NOTE_NOT_FOUND':
      return 'No consultation note exists for this booking yet.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function createConsultationNote(
  clinicId: string,
  bookingId: string,
  content: string,
  token: string,
): Promise<ConsultationNoteResponse> {
  return apiRequest<ConsultationNoteResponse>(
    `/api/v1/clinics/${clinicId}/bookings/${bookingId}/consultation-notes`,
    {
      method: 'POST',
      token,
      body: { content },
      fallbackMessage: defaultMessageFor,
    },
  )
}

export async function getConsultationNote(
  clinicId: string,
  bookingId: string,
  token: string,
): Promise<ConsultationNoteResponse> {
  return apiRequest<ConsultationNoteResponse>(
    `/api/v1/clinics/${clinicId}/bookings/${bookingId}/consultation-notes`,
    { token, fallbackMessage: defaultMessageFor },
  )
}
