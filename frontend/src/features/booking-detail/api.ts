// Client for GET /api/v1/clinics/{clinicId}/bookings/{bookingId}
// staff-console-audit-2026-09-10 P1: backs the entity-context header on booking-scoped staff
// tool pages (mark complete, cancel, consultation note, prescription, external record).
// 046-frontend-api-client: migrated onto the shared apiClient (see its own ApiError export).

import { apiRequest } from '../../lib/apiClient'

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

export async function getBookingDetail(clinicId: string, bookingId: string, token: string): Promise<BookingDetail> {
  return apiRequest<BookingDetail>(`/api/v1/clinics/${clinicId}/bookings/${bookingId}`, { token })
}
