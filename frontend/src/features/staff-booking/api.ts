// Client for POST /api/v1/clinics/{clinicId}/slots/{slotId}/book
// See specs/020-staff-assisted-fixed-time-booking/contracts/staff-booking.md
// Also: POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in
// See specs/025-walk-in-priority-insertion/contracts/walk-in-insertion.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface BookSlotRequest {
  patientId?: string
  patientName?: string
  patientPhone?: string
  appointmentTypeId: string
}

export interface WalkInRequest {
  patientId?: string
  patientName?: string
  patientPhone?: string
  appointmentTypeId: string
  overrideReason?: string
}

export interface BookingResponse {
  id: string
  slotId: string
  patientId: string
  appointmentTypeId: string
  lockedFee: number
  paymentStatus: 'PENDING' | 'PAID'
  createdAt: string
}

export type BookSlotErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'SLOT_NOT_FOUND'; message?: string }
  | { error: 'PATIENT_NOT_FOUND'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'SLOT_ALREADY_BOOKED'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'INVALID_MOBILE_NUMBER'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export type WalkInErrorBody =
  | BookSlotErrorBody
  | { error: 'SESSION_NOT_FOUND'; message?: string }
  | { error: 'NO_SLOT_AVAILABLE'; message?: string }
  | { error: 'OVERRIDE_REASON_REQUIRED'; message?: string }
  | { error: 'NOT_A_FIXED_TIME_SESSION'; message?: string }

export class BookSlotApiError extends Error {
  readonly body: BookSlotErrorBody

  constructor(body: BookSlotErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'BookSlotApiError'
    this.body = body
  }
}

export class WalkInApiError extends Error {
  readonly body: WalkInErrorBody

  constructor(body: WalkInErrorBody) {
    super(defaultWalkInMessageFor(body) ?? body.message)
    this.name = 'WalkInApiError'
    this.body = body
  }
}

function defaultMessageFor(body: BookSlotErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can book this slot.'
    case 'SLOT_NOT_FOUND':
      return 'This slot could not be found.'
    case 'PATIENT_NOT_FOUND':
      return 'This patient could not be found.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'This appointment type could not be found for this doctor.'
    case 'SLOT_ALREADY_BOOKED':
      return 'This slot is already booked.'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is configured for this doctor/appointment type — booking is blocked.'
    case 'INVALID_MOBILE_NUMBER':
      return 'Mobile number must be a valid Indian number.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

function defaultWalkInMessageFor(body: WalkInErrorBody): string {
  switch (body.error) {
    case 'SESSION_NOT_FOUND':
      return 'This session could not be found.'
    case 'NO_SLOT_AVAILABLE':
      return 'No slot is available for a walk-in in this session.'
    case 'OVERRIDE_REASON_REQUIRED':
      return 'An override reason is required to insert this walk-in into a regular slot.'
    case 'NOT_A_FIXED_TIME_SESSION':
      return 'Walk-ins can only be inserted into a fixed-time session.'
    default:
      return defaultMessageFor(body)
  }
}

export async function bookSlot(
  clinicId: string,
  slotId: string,
  payload: BookSlotRequest,
  token: string,
): Promise<BookingResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/slots/${slotId}/book`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let body: BookSlotErrorBody
    try {
      body = (await response.json()) as BookSlotErrorBody
    } catch {
      body = { error: 'SLOT_NOT_FOUND' }
    }
    throw new BookSlotApiError(body)
  }

  return (await response.json()) as BookingResponse
}

export async function insertWalkIn(
  clinicId: string,
  sessionId: string,
  payload: WalkInRequest,
  token: string,
): Promise<BookingResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/sessions/${sessionId}/walk-in`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let body: WalkInErrorBody
    try {
      body = (await response.json()) as WalkInErrorBody
    } catch {
      body = { error: 'SESSION_NOT_FOUND' }
    }
    throw new WalkInApiError(body)
  }

  return (await response.json()) as BookingResponse
}
