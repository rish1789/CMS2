import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PatientBookingOverview } from '../../src/features/patient-bookings/PatientBookingOverview'
import { getMyBooking, type PatientBookingSummary } from '../../src/features/patient-bookings/api'
import { BookingCancellationApiError, cancelBookingAsPatient } from '../../src/features/booking-cancellation/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-bookings/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-bookings/api')>(
    '../../src/features/patient-bookings/api',
  )
  return { ...actual, getMyBooking: vi.fn() }
})

vi.mock('../../src/features/booking-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/booking-cancellation/api')>(
    '../../src/features/booking-cancellation/api',
  )
  return { ...actual, cancelBookingAsPatient: vi.fn() }
})

const mockedGetMyBooking = vi.mocked(getMyBooking)
const mockedCancelAsPatient = vi.mocked(cancelBookingAsPatient)

function booking(overrides: Partial<PatientBookingSummary> = {}): PatientBookingSummary {
  return {
    id: 'booking-1',
    clinicId: 'clinic-1',
    clinicName: 'Sunrise Clinic',
    doctorProfileId: 'doctor-1',
    doctorName: 'Dr. Mehta',
    appointmentTypeName: 'Consultation',
    mode: 'FIXED_TIME',
    sessionDate: '2026-10-01',
    startTime: '11:00:00',
    tokenNumber: null,
    status: 'ACTIVE',
    paymentStatus: 'PENDING',
    lockedFee: 300,
    createdAt: '2026-09-25T00:00:00Z',
    cancellationReason: null,
    visitOutcome: 'SCHEDULED',
    cancellation: { allowed: true, reason: null },
    ...overrides,
  }
}

// 069-patient-visit-outcomes (live-audit findings 1 and 3): the booking detail shows the
// patient's own outcome and only offers cancellation when the server says it can succeed.
describe('PatientBookingOverview', () => {
  beforeEach(() => {
    mockedGetMyBooking.mockReset()
    mockedCancelAsPatient.mockReset()
    storePatientSession({ token: 'a.patient.token', patientAccountId: 'pt-1', email: 'patient@example.com' })
  })

  it('shows a missed appointment as missed - never "Visit complete" - and offers no cancellation', async () => {
    mockedGetMyBooking.mockResolvedValueOnce(
      booking({ visitOutcome: 'NO_SHOW', cancellation: { allowed: false, reason: 'VISIT_RESOLVED' } }),
    )
    render(<PatientBookingOverview bookingId="booking-1" />)

    expect(await screen.findByText('Missed appointment')).toBeInTheDocument()
    expect(screen.queryByText('Visit complete')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancel booking/i })).not.toBeInTheDocument()
    expect(screen.getByText(/already been recorded/i)).toBeInTheDocument()
  })

  it.each([
    ['ALREADY_CANCELLED', /already cancelled/i],
    ['QUEUE_BOOKING', /queue bookings can't be cancelled online/i],
    ['WALK_IN', /walk-in visits are managed by the clinic/i],
    ['CUTOFF_PASSED', /within 2 hours/i],
  ] as const)('explains a %s refusal instead of offering the action', async (reason, text) => {
    mockedGetMyBooking.mockResolvedValueOnce(booking({ cancellation: { allowed: false, reason } }))
    render(<PatientBookingOverview bookingId="booking-1" />)

    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancel booking/i })).not.toBeInTheDocument()
  })

  it('offers the existing cancel flow for an eligible booking', async () => {
    mockedGetMyBooking.mockResolvedValueOnce(booking())
    render(<PatientBookingOverview bookingId="booking-1" />)

    expect(await screen.findByText('Upcoming')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancel booking/i })).toBeInTheDocument()
  })

  it('still shows the server refusal when the displayed eligibility went stale', async () => {
    const user = userEvent.setup()
    mockedGetMyBooking.mockResolvedValue(booking())
    mockedCancelAsPatient.mockRejectedValueOnce(new BookingCancellationApiError({ error: 'CANCELLATION_CUTOFF_PASSED' }))
    render(<PatientBookingOverview bookingId="booking-1" />)

    await user.click(await screen.findByRole('button', { name: /cancel booking/i }))
    await user.selectOptions(screen.getByLabelText(/reason for cancelling/i), 'SCHEDULE_CONFLICT')
    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mockedCancelAsPatient).toHaveBeenCalledTimes(1)
  })

  it('shows an error when the booking cannot be loaded', async () => {
    mockedGetMyBooking.mockRejectedValueOnce(new Error('nope'))
    render(<PatientBookingOverview bookingId="booking-1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not load this booking/i)
  })
})
