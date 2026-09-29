import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NextAppointmentCard } from '../../src/features/patient-bookings/NextAppointmentCard'
import { listMyBookings, type PatientBookingSummary } from '../../src/features/patient-bookings/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-bookings/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-bookings/api')>(
    '../../src/features/patient-bookings/api',
  )
  return { ...actual, listMyBookings: vi.fn() }
})

const mockedListMyBookings = vi.mocked(listMyBookings)

function booking(overrides: Partial<PatientBookingSummary> = {}): PatientBookingSummary {
  return {
    id: 'booking-1',
    clinicId: 'clinic-1',
    clinicName: 'Sunrise Clinic',
    doctorProfileId: 'doctor-1',
    doctorName: 'Dr. Mehta',
    appointmentTypeName: 'Consultation',
    mode: 'FIXED_TIME',
    sessionDate: '2999-01-01',
    startTime: '10:30:00',
    tokenNumber: null,
    status: 'ACTIVE',
    paymentStatus: 'PAID',
    lockedFee: 500,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderCard() {
  render(
    <MemoryRouter>
      <NextAppointmentCard />
    </MemoryRouter>,
  )
}

// 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): restyled to the reference's
// bold "next visit" strip, but the conditional logic under test here is unchanged from before
// this rebuild - render nothing when there's no upcoming booking, never an empty/placeholder
// strip.
describe('NextAppointmentCard', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListMyBookings.mockReset()
    storePatientSession({ token: 'patient-jwt', patientAccountId: 'account-1', email: 'priya@example.com' })
  })

  it('renders nothing when there is no upcoming booking', async () => {
    mockedListMyBookings.mockResolvedValueOnce({ bookings: [], page: 0, pageSize: 50, totalCount: 0 })
    const { container } = render(
      <MemoryRouter>
        <NextAppointmentCard />
      </MemoryRouter>,
    )

    await vi.waitFor(() => {
      expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument()
    })
    expect(screen.queryByText(/your next visit/i)).not.toBeInTheDocument()
    expect(container).toBeEmptyDOMElement()
  })

  it('renders the next upcoming booking with doctor, clinic, date/time, and a link to its details', async () => {
    mockedListMyBookings.mockResolvedValueOnce({
      bookings: [booking()],
      page: 0,
      pageSize: 50,
      totalCount: 1,
    })
    renderCard()

    expect(await screen.findByText('Your next visit')).toBeInTheDocument()
    expect(screen.getByText('Dr. Mehta · Sunrise Clinic')).toBeInTheDocument()
    expect(screen.getByText('10:30', { exact: false })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View details' })).toHaveAttribute('href', '/patient/bookings/booking-1')
  })

  it('omits cancelled bookings from the next-visit strip', async () => {
    mockedListMyBookings.mockResolvedValueOnce({
      bookings: [booking({ status: 'CANCELLED' })],
      page: 0,
      pageSize: 50,
      totalCount: 1,
    })
    const { container } = render(
      <MemoryRouter>
        <NextAppointmentCard />
      </MemoryRouter>,
    )

    await vi.waitFor(() => {
      expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument()
    })
    expect(screen.queryByText(/your next visit/i)).not.toBeInTheDocument()
  })
})
