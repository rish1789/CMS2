// Client for POST /api/v1/clinics/{clinicId}/slots/{slotId}/book
// See specs/020-staff-assisted-fixed-time-booking/contracts/staff-booking.md
// 063-front-desk-walk-in: the 025 per-session walk-in insertion client that lived here is retired;
// walk-ins now go through features/front-desk-walk-in.
// 054-forms-validation-consistency: migrated onto the shared apiClient - both local error
// classes had the identical defaultMessageFor(body) ?? body.message dead-code bug 043 found
// and fixed elsewhere. `BookSlotApiError`/`WalkInApiError` are gone; callers catch the shared
// `ApiError`.

import { apiRequest } from '../../lib/apiClient'

export interface BookSlotRequest {
  patientId?: string
  patientName?: string
  patientPhone?: string
  appointmentTypeId: string
}

export interface BookingResponse {
  id: string
  slotId: string
  patientId: string
  patientName: string
  doctorName: string
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
  | { error: 'CLINIC_NOT_ACCEPTING_APPOINTMENTS'; message?: string }
  | { error: 'INVALID_MOBILE_NUMBER'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as BookSlotErrorBody | undefined)?.error
  switch (error) {
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
    case 'CLINIC_NOT_ACCEPTING_APPOINTMENTS':
      return 'This clinic is not accepting appointments.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function bookSlot(
  clinicId: string,
  slotId: string,
  payload: BookSlotRequest,
  token: string,
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>(`/api/v1/clinics/${clinicId}/slots/${slotId}/book`, {
    method: 'POST',
    token,
    body: payload,
    fallbackMessage: defaultMessageFor,
  })
}
