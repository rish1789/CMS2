// Client for POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings
// See specs/022-queue-token-booking/contracts/queue-booking.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface QueueBookSlotRequest {
  patientId?: string
  patientName?: string
  patientPhone?: string
  appointmentTypeId: string
}

export interface QueueBookingResponse {
  id: string
  slotId: string
  tokenNumber: number
  patientId: string
  appointmentTypeId: string
  lockedFee: number
  paymentStatus: 'PENDING' | 'PAID'
  createdAt: string
}

export type QueueBookSlotErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_QUEUE_SESSION'; message?: string }
  | { error: 'PATIENT_NOT_FOUND'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'INVALID_MOBILE_NUMBER'; message?: string }
  | { error: 'TOKEN_ISSUANCE_FAILED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class QueueBookSlotApiError extends Error {
  readonly body: QueueBookSlotErrorBody

  constructor(body: QueueBookSlotErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'QueueBookSlotApiError'
    this.body = body
  }
}

function defaultMessageFor(body: QueueBookSlotErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can book into this queue.'
    case 'SESSION_NOT_FOUND':
      return 'This queue session could not be found.'
    case 'NOT_A_QUEUE_SESSION':
      return 'This session is not a queue/token session.'
    case 'PATIENT_NOT_FOUND':
      return 'This patient could not be found.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'This appointment type could not be found for this doctor.'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is configured for this doctor/appointment type — booking is blocked.'
    case 'INVALID_MOBILE_NUMBER':
      return 'Mobile number must be a valid Indian number.'
    case 'TOKEN_ISSUANCE_FAILED':
      return 'Could not issue a queue token right now — please try again shortly.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function bookQueueSlot(
  clinicId: string,
  sessionId: string,
  payload: QueueBookSlotRequest,
  token: string,
): Promise<QueueBookingResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/queue-bookings`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(payload),
    },
  )

  if (!response.ok) {
    let body: QueueBookSlotErrorBody
    try {
      body = (await response.json()) as QueueBookSlotErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new QueueBookSlotApiError(body)
  }

  return (await response.json()) as QueueBookingResponse
}
