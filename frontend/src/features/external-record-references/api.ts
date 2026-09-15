// Client for POST/GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references
// See specs/036-external-record-reference/contracts/external-record-reference.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface ExternalRecordReferenceFields {
  recordType: string
  sourceProvider: string
  recordDate: string
  summary: string
}

export interface ExternalRecordReferenceResponse extends ExternalRecordReferenceFields {
  id: string
  bookingId: string
  doctorProfileId: string
  createdAt: string
}

export type ExternalRecordReferenceErrorBody =
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  // _diagnostics [LOW] - [EXTERNAL_RECORD_REFERENCE] - [ERROR_CODE_OVERGENERALIZATION]: a
  // distinct sentinel for "the error body itself failed to parse as JSON", never conflated with
  // the real BOOKING_NOT_FOUND code.
  | { error: 'UNKNOWN'; message?: string }

export class ExternalRecordReferenceApiError extends Error {
  readonly body: ExternalRecordReferenceErrorBody

  constructor(body: ExternalRecordReferenceErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'ExternalRecordReferenceApiError'
    this.body = body
  }
}

function defaultMessageFor(body: ExternalRecordReferenceErrorBody): string {
  switch (body.error) {
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'FORBIDDEN':
      return 'Only the treating doctor may write or view references for this booking.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function createExternalRecordReference(
  clinicId: string,
  bookingId: string,
  fields: ExternalRecordReferenceFields,
  token: string,
): Promise<ExternalRecordReferenceResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/external-record-references`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(fields),
    },
  )

  if (!response.ok) {
    let body: ExternalRecordReferenceErrorBody
    try {
      body = (await response.json()) as ExternalRecordReferenceErrorBody
    } catch {
      body = { error: 'UNKNOWN' }
    }
    throw new ExternalRecordReferenceApiError(body)
  }

  return (await response.json()) as ExternalRecordReferenceResponse
}

export async function listExternalRecordReferences(
  clinicId: string,
  bookingId: string,
  token: string,
): Promise<ExternalRecordReferenceResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/external-record-references`,
    {
      headers: { Authorization: `Bearer ${token}` },
    },
  )

  if (!response.ok) {
    let body: ExternalRecordReferenceErrorBody
    try {
      body = (await response.json()) as ExternalRecordReferenceErrorBody
    } catch {
      body = { error: 'UNKNOWN' }
    }
    throw new ExternalRecordReferenceApiError(body)
  }

  return (await response.json()) as ExternalRecordReferenceResponse[]
}
