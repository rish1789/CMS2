// Client for GET /api/v1/patients/bookings ("My bookings")

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type BookingStatus = 'ACTIVE' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'PAID'
export type ScheduleMode = 'FIXED_TIME' | 'QUEUE'

// 069-patient-visit-outcomes: the patient's own visit outcome, derived by the server from the
// booking, the patient's own slot and the server's operational date - never computed here.
export type VisitOutcome = 'SCHEDULED' | 'CHECKED_IN' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED' | 'NOT_RECORDED'

export type CancellationRefusal = 'ALREADY_CANCELLED' | 'VISIT_RESOLVED' | 'QUEUE_BOOKING' | 'WALK_IN' | 'CUTOFF_PASSED'

// Advisory only - the cancel endpoint always re-checks.
export interface CancellationEligibility {
  allowed: boolean
  reason: CancellationRefusal | null
}

export interface PatientBookingSummary {
  id: string
  clinicId: string
  clinicName: string
  doctorProfileId: string
  doctorName: string
  appointmentTypeName: string
  mode: ScheduleMode
  sessionDate: string
  startTime: string | null
  tokenNumber: number | null
  status: BookingStatus
  paymentStatus: PaymentStatus
  lockedFee: number
  createdAt: string
  // 062-rejected-clinic-gating: why a CANCELLED booking was cancelled; null when no reason was recorded.
  cancellationReason: string | null
  visitOutcome: VisitOutcome
  cancellation: CancellationEligibility
}

export interface PatientBookingListResult {
  bookings: PatientBookingSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export interface ListMyBookingsParams {
  page?: number
  size?: number
}

export async function listMyBookings(token: string, params: ListMyBookingsParams = {}): Promise<PatientBookingListResult> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const queryString = query.toString()

  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings${queryString ? `?${queryString}` : ''}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load your bookings.')
  }

  return (await response.json()) as PatientBookingListResult
}

// 069-patient-visit-outcomes: one of the caller's own bookings - 404 for anyone else's.
export async function getMyBooking(bookingId: string, token: string): Promise<PatientBookingSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load this booking.')
  }

  return (await response.json()) as PatientBookingSummary
}
