import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BookingContextHeader } from '../../src/features/booking-detail/BookingContextHeader'
import { getBookingDetail, BookingDetailApiError, type BookingDetail } from '../../src/features/booking-detail/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/booking-detail/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/booking-detail/api')>(
    '../../src/features/booking-detail/api',
  )
  return { ...actual, getBookingDetail: vi.fn() }
})

const mockedGetBookingDetail = vi.mocked(getBookingDetail)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

const DETAIL: BookingDetail = {
  bookingId: BOOKING_ID,
  sessionId: 'session-1',
  patientId: 'patient-1',
  patientName: 'Asha Rao',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Priya Nair',
  sessionDate: '2026-09-10',
  mode: 'FIXED_TIME',
  appointmentTypeName: 'General consult',
}

function renderHeader() {
  render(
    <MemoryRouter>
      <BookingContextHeader clinicId={CLINIC_ID} bookingId={BOOKING_ID} />
    </MemoryRouter>,
  )
}

describe('BookingContextHeader', () => {
  beforeEach(() => {
    mockedGetBookingDetail.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('shows the patient name, appointment context, and a link back to the session', async () => {
    mockedGetBookingDetail.mockResolvedValueOnce(DETAIL)
    renderHeader()

    expect(await screen.findByRole('heading', { name: 'Asha Rao' })).toBeInTheDocument()
    expect(screen.getByText(/general consult/i)).toBeInTheDocument()

    const backLink = screen.getByRole('link', { name: /back to dr\. priya nair's session/i })
    expect(backLink).toHaveAttribute('href', '/staff/clinics/clinic-1/day-sheet/session-1')
  })

  it('renders nothing (no redundant error banner) when the lookup fails', async () => {
    mockedGetBookingDetail.mockRejectedValueOnce(new BookingDetailApiError(404))
    const { container } = render(
      <MemoryRouter>
        <BookingContextHeader clinicId={CLINIC_ID} bookingId={BOOKING_ID} />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement()
    })
  })
})
