// Client for GET /api/v1/patients/clinics/{clinicId}/slots and
// POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book
// See specs/021-patient-self-service-booking/contracts/patient-booking.md
// 046-frontend-api-client: migrated onto the shared apiClient (see its own ApiError export).
// This was the file where the defaultMessageFor(body) ?? body.message dead-code bug was
// directly confirmed - defaultMessageFor always returned a string, so the backend's real
// body.message was unreachable. Fixed by the shared client's message-priority order.

import { apiRequest } from '../../lib/apiClient'

export interface AppointmentTypeOption {
  id: string
  doctorProfileId: string
  name: string
  // 068-per-clinic-fees: the effective price at this clinic; null when not bookable here.
  fee: number | null
}

export interface OpenSlot {
  slotId: string
  doctorProfileId: string
  doctorName: string
  sessionDate: string
  startTime: string
  endTime: string
  appointmentTypes: AppointmentTypeOption[]
}

export interface OpenSlotListResult {
  slots: OpenSlot[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListOpenSlotsParams {
  page?: number
  size?: number
  // patient-slot-booking-date-logic: exact-day filter (YYYY-MM-DD) driving the date-strip
  // picker. Every date, present or absent, is floored to today-or-later server-side regardless.
  date?: string
}

// patient-booking-flow-rebuild: the Queue-mode analog of OpenSlot - one browsable Session
// instead of one browsable Slot (Queue tokens are issued at booking time, not pre-listed).
export interface QueueSession {
  sessionId: string
  doctorProfileId: string
  doctorName: string
  sessionDate: string
  startTime: string
  endTime: string
  appointmentTypes: AppointmentTypeOption[]
}

export interface QueueSessionListResult {
  sessions: QueueSession[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListQueueSessionsParams {
  page?: number
  size?: number
}

// patient-booking-flow-rebuild: replaces the "type a Session ID" field on the queue-booking
// entry flow - browses today-or-later Queue-mode sessions at a clinic, optionally pre-filtered
// to one doctor (a Discovery search result linking straight in).
export async function listQueueSessions(
  clinicId: string,
  token: string,
  doctorId?: string,
  params: ListQueueSessionsParams = {},
): Promise<QueueSessionListResult> {
  const query = new URLSearchParams()
  if (doctorId) query.set('doctorId', doctorId)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  return apiRequest<QueueSessionListResult>(
    `/api/v1/patients/clinics/${clinicId}/queue-sessions${queryString ? `?${queryString}` : ''}`,
    { token, fallbackMessage: 'Could not load queue sessions.' },
  )
}

// pagination-unification-2026-09-10: paginated server-side - a clinic-wide open-slot listing
// with no doctor filter can span every Fixed-Time doctor's entire remaining inventory.
export async function listOpenSlots(
  clinicId: string,
  token: string,
  doctorId?: string,
  params: ListOpenSlotsParams = {},
): Promise<OpenSlotListResult> {
  const query = new URLSearchParams()
  if (doctorId) query.set('doctorId', doctorId)
  if (params.date) query.set('date', params.date)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  return apiRequest<OpenSlotListResult>(
    `/api/v1/patients/clinics/${clinicId}/slots${queryString ? `?${queryString}` : ''}`,
    { token, fallbackMessage: 'Could not load open slots.' },
  )
}

export interface PatientBookSlotRequest {
  patientName: string
  appointmentTypeId: string
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
  | { error: 'SLOT_NOT_FOUND'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'SLOT_ALREADY_BOOKED'; message?: string }
  | { error: 'SLOT_DATE_IN_THE_PAST'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'CLINIC_NOT_ACCEPTING_APPOINTMENTS'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as BookSlotErrorBody | undefined)?.error
  switch (error) {
    case 'SLOT_NOT_FOUND':
      return 'This slot could not be found.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'This appointment type could not be found for this doctor.'
    case 'SLOT_ALREADY_BOOKED':
      return 'This slot is no longer available.'
    case 'SLOT_DATE_IN_THE_PAST':
      return 'This slot is dated in the past and can no longer be booked. Please pick another date.'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is configured for this doctor/appointment type — booking is blocked.'
    case 'UNAUTHORIZED':
      return 'Please log in to book a slot.'
    case 'CLINIC_NOT_ACCEPTING_APPOINTMENTS':
      return 'This clinic is not accepting appointments.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function bookSlot(
  clinicId: string,
  slotId: string,
  payload: PatientBookSlotRequest,
  token: string,
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>(`/api/v1/patients/clinics/${clinicId}/slots/${slotId}/book`, {
    method: 'POST',
    token,
    body: payload,
    fallbackMessage: defaultMessageFor,
  })
}
