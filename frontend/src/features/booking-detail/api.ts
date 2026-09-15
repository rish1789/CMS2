// Client for GET /api/v1/clinics/{clinicId}/bookings/{bookingId}
// staff-console-audit-2026-09-10 P1: backs the entity-context header on booking-scoped staff
// tool pages (mark complete, cancel, consultation note, prescription, external record).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface BookingDetail {
  bookingId: string
  sessionId: string
  patientId: string
  patientName: string
  doctorProfileId: string
  doctorName: string
  sessionDate: string
  mode: 'FIXED_TIME' | 'QUEUE'
  appointmentTypeName: string
}

export class BookingDetailApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status}).`)
    this.name = 'BookingDetailApiError'
    this.status = status
  }
}

export async function getBookingDetail(clinicId: string, bookingId: string, token: string): Promise<BookingDetail> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/bookings/${bookingId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new BookingDetailApiError(response.status)
  }
  return (await response.json()) as BookingDetail
}
