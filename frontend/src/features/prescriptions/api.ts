// Client for POST/GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions
// See specs/035-prescription-and-items-creation/contracts/prescription.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PrescriptionItemInput {
  medicationName: string
  dosage: string
  frequency: string
  duration: string
  instructions?: string
}

export interface PrescriptionItemResponse {
  id: string
  medicationName: string
  dosage: string
  frequency: string
  duration: string
  instructions: string | null
}

export interface PrescriptionResponse {
  id: string
  bookingId: string
  doctorProfileId: string
  createdAt: string
  items: PrescriptionItemResponse[]
}

export type PrescriptionErrorBody =
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'PRESCRIPTION_ITEM_REQUIRED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  // _diagnostics [LOW] - [PRESCRIPTION] - [ERROR_CODE_OVERGENERALIZATION]: a distinct sentinel
  // for "the error body itself failed to parse as JSON", never conflated with the real
  // BOOKING_NOT_FOUND code.
  | { error: 'UNKNOWN'; message?: string }

export class PrescriptionApiError extends Error {
  readonly body: PrescriptionErrorBody

  constructor(body: PrescriptionErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'PrescriptionApiError'
    this.body = body
  }
}

function defaultMessageFor(body: PrescriptionErrorBody): string {
  switch (body.error) {
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'FORBIDDEN':
      return 'Only the treating doctor may write or view prescriptions for this booking.'
    case 'PRESCRIPTION_ITEM_REQUIRED':
      return 'Add at least one medication item.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function createPrescription(
  clinicId: string,
  bookingId: string,
  items: PrescriptionItemInput[],
  token: string,
): Promise<PrescriptionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/prescriptions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ items }),
  })

  if (!response.ok) {
    let body: PrescriptionErrorBody
    try {
      body = (await response.json()) as PrescriptionErrorBody
    } catch {
      body = { error: 'UNKNOWN' }
    }
    throw new PrescriptionApiError(body)
  }

  return (await response.json()) as PrescriptionResponse
}

export async function listPrescriptions(
  clinicId: string,
  bookingId: string,
  token: string,
): Promise<PrescriptionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/prescriptions`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    let body: PrescriptionErrorBody
    try {
      body = (await response.json()) as PrescriptionErrorBody
    } catch {
      body = { error: 'UNKNOWN' }
    }
    throw new PrescriptionApiError(body)
  }

  return (await response.json()) as PrescriptionResponse[]
}
