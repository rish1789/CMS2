// Client for POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings
// See specs/022-queue-token-booking/contracts/queue-booking.md

import { rateLimitMessage } from '../../lib/rateLimitMessage'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PatientQueueBookSlotRequest {
  patientName: string
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
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_QUEUE_SESSION'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'CLINIC_NOT_ACCEPTING_APPOINTMENTS'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'TOKEN_ISSUANCE_FAILED'; message?: string }
  // 060-booking-abuse-prevention: BOOKING_LIMIT_REACHED's message is already the exact,
  // non-accusatory text to show (FR-005); RATE_LIMITED additionally carries retryAfterSeconds.
  | { error: 'BOOKING_LIMIT_REACHED'; message?: string }
  | { error: 'RATE_LIMITED'; message?: string; retryAfterSeconds?: number }

export class QueueBookSlotApiError extends Error {
  readonly body: QueueBookSlotErrorBody

  constructor(body: QueueBookSlotErrorBody) {
    super(defaultMessageFor(body))
    this.name = 'QueueBookSlotApiError'
    this.body = body
  }
}

function defaultMessageFor(body: QueueBookSlotErrorBody): string {
  switch (body.error) {
    case 'SESSION_NOT_FOUND':
      return 'This queue session could not be found.'
    case 'NOT_A_QUEUE_SESSION':
      return 'This session is not a queue/token session.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'This appointment type could not be found for this doctor.'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is configured for this doctor/appointment type — booking is blocked.'
    case 'UNAUTHORIZED':
      return 'Please log in to book into this queue.'
    case 'TOKEN_ISSUANCE_FAILED':
      return 'Could not issue a queue token right now — please try again shortly.'
    case 'BOOKING_LIMIT_REACHED':
      return body.message ?? "You've reached your current appointment limit."
    case 'RATE_LIMITED':
      return rateLimitMessage(body.retryAfterSeconds)
    case 'CLINIC_NOT_ACCEPTING_APPOINTMENTS':
      return 'This clinic is not accepting appointments.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function bookQueueSlot(
  clinicId: string,
  sessionId: string,
  payload: PatientQueueBookSlotRequest,
  token: string,
): Promise<QueueBookingResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/sessions/${sessionId}/queue-bookings`,
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
