// Client for POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel (staff)
// and POST /api/v1/patients/bookings/{bookingId}/cancel (patient)
// See specs/028-individual-booking-cancellation/contracts/booking-cancellation.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface BookingCancellationResponse {
  id: string
  slotId: string
  patientId: string
  appointmentTypeId: string
  lockedFee: number
  paymentStatus: 'PENDING' | 'PAID'
  status: 'ACTIVE' | 'CANCELLED'
  createdAt: string
}

export type CancellationReason =
  | 'SCHEDULE_CONFLICT'
  | 'FEELING_BETTER'
  | 'FOUND_ANOTHER_PROVIDER'
  | 'PERSONAL_EMERGENCY'
  | 'OTHER'

export interface CancelBookingRequest {
  reason: CancellationReason
  reasonDetail?: string
}

export type BookingCancellationErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'BOOKING_NOT_FOUND'; message?: string }
  | { error: 'NOT_A_FIXED_TIME_SESSION'; message?: string }
  | { error: 'BOOKING_NOT_CANCELLABLE'; message?: string }
  | { error: 'CANCELLATION_CUTOFF_PASSED'; message?: string }
  | { error: 'CANCELLATION_REASON_REQUIRED'; message?: string }
  | { error: 'INVALID_CANCELLATION_REASON'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class BookingCancellationApiError extends Error {
  readonly body: BookingCancellationErrorBody

  constructor(body: BookingCancellationErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'BookingCancellationApiError'
    this.body = body
  }
}

function defaultMessageFor(body: BookingCancellationErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'You do not have access to this clinic.'
    case 'BOOKING_NOT_FOUND':
      return 'This booking could not be found.'
    case 'NOT_A_FIXED_TIME_SESSION':
      return 'Only fixed-time bookings can be cancelled here.'
    case 'BOOKING_NOT_CANCELLABLE':
      return 'This booking can no longer be cancelled.'
    case 'CANCELLATION_CUTOFF_PASSED':
      return 'This booking is less than 2 hours away — please contact the clinic to cancel.'
    case 'CANCELLATION_REASON_REQUIRED':
      return 'Please select a reason for cancelling.'
    case 'INVALID_CANCELLATION_REASON':
      return 'That cancellation reason is not recognized. Please pick one from the list.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

async function postCancel(url: string, token: string, body?: CancelBookingRequest): Promise<BookingCancellationResponse> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })

  if (!response.ok) {
    let errorBody: BookingCancellationErrorBody
    try {
      errorBody = (await response.json()) as BookingCancellationErrorBody
    } catch {
      errorBody = { error: 'BOOKING_NOT_FOUND' }
    }
    throw new BookingCancellationApiError(errorBody)
  }

  return (await response.json()) as BookingCancellationResponse
}

export function cancelBookingAsStaff(
  clinicId: string,
  bookingId: string,
  token: string,
): Promise<BookingCancellationResponse> {
  return postCancel(`${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}/cancel`, token)
}

export function cancelBookingAsPatient(
  bookingId: string,
  token: string,
  request: CancelBookingRequest,
): Promise<BookingCancellationResponse> {
  return postCancel(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/cancel`, token, request)
}
