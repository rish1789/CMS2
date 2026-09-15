// Client for GET /api/v1/patients/clinics/{clinicId}/slots and
// POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book
// See specs/021-patient-self-service-booking/contracts/patient-booking.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface AppointmentTypeOption {
  id: string
  doctorProfileId: string
  name: string
  feeOverride: number | null
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

  const response = await fetch(
    `${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/queue-sessions${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )

  if (!response.ok) {
    throw new Error('Could not load queue sessions.')
  }

  return (await response.json()) as QueueSessionListResult
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

  const response = await fetch(
    `${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/slots${queryString ? `?${queryString}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )

  if (!response.ok) {
    throw new Error('Could not load open slots.')
  }

  return (await response.json()) as OpenSlotListResult
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
  | { error: 'UNAUTHORIZED'; message?: string }

export class BookSlotApiError extends Error {
  readonly body: BookSlotErrorBody

  constructor(body: BookSlotErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'BookSlotApiError'
    this.body = body
  }
}

function defaultMessageFor(body: BookSlotErrorBody): string {
  switch (body.error) {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/slots/${slotId}/book`, {
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
