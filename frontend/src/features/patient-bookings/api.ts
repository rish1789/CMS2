// Client for GET /api/v1/patients/bookings ("My bookings")

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type BookingStatus = 'ACTIVE' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'PAID'
export type ScheduleMode = 'FIXED_TIME' | 'QUEUE'

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
