import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionSlotsView } from '../../src/features/day-sheet/SessionSlotsView'
import { getDaySheet, type SessionDaySheet } from '../../src/features/day-sheet/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { cancelSession } from '../../src/features/session-cancellation/api'
import { cancelFromCutoff } from '../../src/features/partial-session-cancellation/api'

vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>(
    '../../src/features/day-sheet/api',
  )
  return { ...actual, getDaySheet: vi.fn() }
})

vi.mock('../../src/features/session-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-cancellation/api')>(
    '../../src/features/session-cancellation/api',
  )
  return { ...actual, cancelSession: vi.fn() }
})

vi.mock('../../src/features/partial-session-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/partial-session-cancellation/api')>(
    '../../src/features/partial-session-cancellation/api',
  )
  return { ...actual, cancelFromCutoff: vi.fn() }
})

const mockedGetDaySheet = vi.mocked(getDaySheet)
const mockedCancelSession = vi.mocked(cancelSession)
const mockedCancelFromCutoff = vi.mocked(cancelFromCutoff)

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/day-sheet/session-1']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/day-sheet/:sessionId" element={<SessionSlotsView />} />
      </Routes>
    </MemoryRouter>,
  )
}

const baseDaySheet: SessionDaySheet = {
  sessionId: 'session-1',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Priya Nair',
  sessionDate: '2026-09-10',
  mode: 'FIXED_TIME',
  slots: [],
}

describe('SessionSlotsView (041-staff-console-pickers T013/US2)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedGetDaySheet.mockReset()
    mockedCancelSession.mockReset()
    mockedCancelFromCutoff.mockReset()
  })

  it('shows the correct doctor name and date from the API response alone - no navigation state involved (042-day-sheet-hardening FR-010)', async () => {
    // renderWithSession() below navigates with a plain path and no `state`, simulating a
    // direct URL visit or a page refresh - exactly the scenario this fix closes.
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    renderWithSession()

    expect(await screen.findByRole('heading', { name: 'Dr. Priya Nair' })).toBeInTheDocument()
    const expectedDate = new Date('2026-09-10T00:00:00').toLocaleDateString(undefined, {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    })
    expect(screen.getByText(expectedDate)).toBeInTheDocument()
  })

  /** staff-console-redesign-2026-09-10: continues ClinicShell's own breadcrumb with "Doctors / {doctorName}". */
  it('shows a Doctors breadcrumb linking back to the Doctors page', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    renderWithSession()

    await screen.findByRole('heading', { name: 'Dr. Priya Nair' })
    const nav = screen.getByRole('navigation', { name: /breadcrumb/i })
    expect(within(nav).getByRole('link', { name: /^doctors$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/doctors',
    )
    expect(within(nav).getByText('Dr. Priya Nair')).toBeInTheDocument()
  })

  it("renders a booked slot's patient name and action links to the correct existing routes", async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-1',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          isBuffer: false,
          booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText(/Asha Rao/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^cancel$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/bookings/booking-1/cancel',
    )
    // "Consultation note" is now behind the "More" menu (042-day-sheet-hardening US3 fix) - open it first.
    await userEvent.click(screen.getByText('More'))
    expect(screen.getByRole('link', { name: /consultation note/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/bookings/booking-1/consultation-note',
    )
  })

  /** staff-console-redesign-2026-09-10: the flat row list became a real Sr/Time/Patient/Status table. */
  it('renders slots as a numbered table with a status badge and a total/booked summary line', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-1',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          isBuffer: false,
          booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
        },
        {
          slotId: 'slot-2',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'OPEN',
          isBuffer: false,
          booking: null,
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText('2 total · 1 booked')).toBeInTheDocument()
    const rows = screen.getAllByRole('row').slice(1) // drop the header row
    expect(rows[0]).toHaveTextContent('1')
    expect(rows[1]).toHaveTextContent('2')
    expect(screen.getByText('Booked')).toBeInTheDocument()
    expect(screen.getByText('Open')).toBeInTheDocument()
  })

  it('marks a buffer slot as reserved capacity with no Book action', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-2',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'OPEN',
          isBuffer: true,
          booking: null,
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText(/reserved capacity/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^book$/i })).not.toBeInTheDocument()
  })

  it('offers a direct Book link for an ordinary open Fixed-Time slot', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-3',
          startTime: '09:30:00',
          endTime: '09:45:00',
          tokenNumber: null,
          status: 'OPEN',
          isBuffer: false,
          booking: null,
        },
      ],
    })
    renderWithSession()

    const bookLink = await screen.findByRole('link', { name: /^book$/i })
    expect(bookLink).toHaveAttribute('href', '/staff/clinics/clinic-1/slots/slot-3/book?doctorProfileId=doctor-1')
  })

  it('renders session-level actions inline, with no separate Danger zone box', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    renderWithSession()

    expect(await screen.findByRole('link', { name: /insert a walk-in/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/cancel slots from/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancel entire session/i })).toBeInTheDocument()
    expect(screen.queryByText(/danger zone/i)).not.toBeInTheDocument()
  })

  const bookedSlot = {
    slotId: 'slot-1',
    startTime: '09:00:00',
    endTime: '09:15:00',
    tokenNumber: null,
    status: 'BOOKED' as const,
    isBuffer: false,
    booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
  }

  it('requires confirmation before cancelling the whole session, and refreshes the slot list after confirming', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, slots: [bookedSlot] })
    mockedCancelSession.mockResolvedValueOnce({ sessionId: 'session-1', bookingsCancelled: 2 })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('button', { name: /cancel entire session/i }))
    expect(mockedCancelSession).not.toHaveBeenCalled()
    expect(screen.getByText(/are you sure/i)).toBeInTheDocument()

    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, slots: [] })
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByText(/2 bookings cancelled/i)).toBeInTheDocument()
    expect(mockedGetDaySheet).toHaveBeenCalledTimes(2)
  })

  it('shows the nothing-to-cancel message immediately when there are no booked slots, with no confirm step and no API call', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('button', { name: /cancel entire session/i }))

    expect(await screen.findByText(/nothing left to cancel/i)).toBeInTheDocument()
    expect(mockedCancelSession).not.toHaveBeenCalled()
    expect(screen.queryByText(/are you sure/i)).not.toBeInTheDocument()
  })

  it('submits the inline from/to cancel-range control (after confirming) and refreshes the slot list', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    mockedCancelFromCutoff.mockResolvedValueOnce({ sessionId: 'session-1', bookingsCancelled: 1 })
    const user = userEvent.setup()
    renderWithSession()

    await user.type(await screen.findByLabelText(/cancel slots from/i), '14:00')
    await user.type(screen.getByLabelText(/^to$/i), '15:00')
    await user.click(screen.getByRole('button', { name: /cancel these slots/i }))
    expect(mockedCancelFromCutoff).not.toHaveBeenCalled()

    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, slots: [] })
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByText(/1 booking cancelled/i)).toBeInTheDocument()
    expect(mockedCancelFromCutoff).toHaveBeenCalledWith('clinic-1', 'session-1', '14:00:00', '15:00:00', 'staff-jwt')
    expect(mockedGetDaySheet).toHaveBeenCalledTimes(2)
  })
})
