// Client for GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position (staff)
// and GET /api/v1/patients/bookings/{bookingId}/queue-position (patient)
// See specs/027-queue-position-tracking/contracts/queue-position-tracking.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface QueuePositionResponse {
  bookingId: string
  applicable: boolean
  position: number | null
}

export type QueuePositionErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class QueuePositionApiError extends Error {
  readonly body: QueuePositionErrorBody

  constructor(body: QueuePositionErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'QueuePositionApiError'
    this.body = body
  }
}

function defaultMessageFor(body: QueuePositionErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'You do not have access to this clinic.'
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

async function fetchQueuePosition(url: string, token: string): Promise<QueuePositionResponse> {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: QueuePositionErrorBody
    try {
      body = (await response.json()) as QueuePositionErrorBody
    } catch {
      body = { error: 'BOOKING_NOT_FOUND' }
    }
    throw new QueuePositionApiError(body)
  }

  return (await response.json()) as QueuePositionResponse
}

export function getQueuePositionAsStaff(clinicId: string, bookingId: string, token: string): Promise<QueuePositionResponse> {
  return fetchQueuePosition(`${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/queue-position`, token)
}

export function getQueuePositionAsPatient(bookingId: string, token: string): Promise<QueuePositionResponse> {
  return fetchQueuePosition(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/queue-position`, token)
}
