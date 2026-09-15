// Client for POST/GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes
// See specs/034-consultation-note-creation/contracts/consultation-note.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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

export class ConsultationNoteApiError extends Error {
  readonly body: ConsultationNoteErrorBody

  constructor(body: ConsultationNoteErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'ConsultationNoteApiError'
    this.body = body
  }
}

function defaultMessageFor(body: ConsultationNoteErrorBody): string {
  switch (body.error) {
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
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/consultation-notes`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content }),
    },
  )

  if (!response.ok) {
    let body: ConsultationNoteErrorBody
    try {
      body = (await response.json()) as ConsultationNoteErrorBody
    } catch {
      body = { error: 'BOOKING_NOT_FOUND' }
    }
    throw new ConsultationNoteApiError(body)
  }

  return (await response.json()) as ConsultationNoteResponse
}

export async function getConsultationNote(
  clinicId: string,
  bookingId: string,
  token: string,
): Promise<ConsultationNoteResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/consultation-notes`,
    {
      headers: { Authorization: `Bearer ${token}` },
    },
  )

  if (!response.ok) {
    let body: ConsultationNoteErrorBody
    try {
      body = (await response.json()) as ConsultationNoteErrorBody
    } catch {
      body = { error: 'UNKNOWN' }
    }
    throw new ConsultationNoteApiError(body)
  }

  return (await response.json()) as ConsultationNoteResponse
}
