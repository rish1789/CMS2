import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionSlotsView } from '../../src/features/day-sheet/SessionSlotsView'
import { getDaySheet, type SessionDaySheet } from '../../src/features/day-sheet/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { cancelSession } from '../../src/features/session-cancellation/api'
import { cancelFromCutoff } from '../../src/features/partial-session-cancellation/api'
import type { StaffRole } from '../../src/components/RoleBadge'
import type { ClinicShellOutletContext } from '../../src/routes/staff/ClinicShell'

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

// 057-day-sheet-status-overhaul: SessionSlotsView now reads `role` via useOutletContext (the
// same context ClinicShell provides in the real app) - a nested route + an Outlet-rendering
// parent is required here, not a bare element, or useOutletContext returns undefined.
function OutletContextProvider({ role }: { role?: StaffRole }) {
  return <Outlet context={{ role } satisfies ClinicShellOutletContext} />
}

function renderWithSession(role: StaffRole = 'ClinicAdmin') {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/day-sheet/session-1']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId" element={<OutletContextProvider role={role} />}>
          <Route path="day-sheet/:sessionId" element={<SessionSlotsView />} />
        </Route>
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
  cancelled: false,
  cancelledRanges: [],
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
          booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText(/Asha Rao/)).toBeInTheDocument()
    // 057-day-sheet-status-overhaul: the old inline "Cancel" link is gone, replaced by a
    // selection checkbox (batch-cancel UI) - see the dedicated checkbox tests below.
    expect(screen.getByRole('checkbox', { name: /select.*for cancellation/i })).toBeInTheDocument()
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
          booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
        },
        {
          slotId: 'slot-2',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'OPEN',
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

  // day-sheet-slot-ordering-fix: the summary count must count every slot that ever left OPEN
  // (a live booking), not just ones still literally BOOKED - a slot that auto-flipped to
  // NO_SHOW or COMPLETED still has a real patient booking behind it.
  it('counts NO_SHOW and COMPLETED slots (a live booking left the BOOKED status) toward the booked summary', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-1',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'NO_SHOW',
          booking: { bookingId: 'booking-1', patientId: 'patient-1', patientName: 'Asha Rao' },
        },
        {
          slotId: 'slot-2',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'COMPLETED',
          booking: { bookingId: 'booking-2', patientId: 'patient-2', patientName: 'Rohan Iyer' },
        },
        {
          slotId: 'slot-3',
          startTime: '09:30:00',
          endTime: '09:45:00',
          tokenNumber: null,
          status: 'OPEN',
          booking: null,
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText('3 total · 2 booked')).toBeInTheDocument()
  })

  it('shows the real patient on a booked slot', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-2',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-2', patientId: 'patient-2', patientName: 'Karan Singh' },
        },
      ],
    })
    renderWithSession()

    expect(await screen.findByText('Karan Singh')).toBeInTheDocument()
    expect(screen.queryByText(/reserved capacity/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/no direct booking/i)).not.toBeInTheDocument()
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
          booking: null,
        },
      ],
    })
    renderWithSession()

    const bookLink = await screen.findByRole('link', { name: /^book$/i })
    expect(bookLink).toHaveAttribute('href', '/staff/clinics/clinic-1/slots/slot-3/book?doctorProfileId=doctor-1')
  })

  // 057-day-sheet-status-overhaul: Appeared is never time-gated (a patient can arrive before
  // the slot's own scheduled start) - unlike the old "Mark complete", "Mark appeared" always
  // shows for a BOOKED row regardless of whether its scheduled start has passed.
  it('offers Mark appeared (and a cancel-selection checkbox) for a booked slot even when its scheduled start is still in the future', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2099-01-01',
      slots: [
        {
          slotId: 'slot-4',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-4', patientId: 'patient-4', patientName: 'Future Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Future Patient')
    expect(screen.getByRole('link', { name: /^appeared$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/sessions/session-1/operations?slotId=slot-4&bookingId=booking-4',
    )
    expect(screen.queryByText(/starts at/i)).not.toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /select.*for cancellation/i })).toBeInTheDocument()
  })

  // FR-008: ClinicAdmin/Operations' pre-057 direct BOOKED->COMPLETED path is additive, not
  // replaced by Mark appeared - it stays reachable once the slot has actually started, and a
  // Doctor never gets it (they only ever reach Complete via an Appeared slot, FR-006).
  it('offers a direct Mark complete link for a started booked slot to staff', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2020-01-01',
      slots: [
        {
          slotId: 'slot-4b',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-4b', patientId: 'patient-4b', patientName: 'Started Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Started Patient')
    expect(screen.getByRole('link', { name: /^complete$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/sessions/session-1/operations?slotId=slot-4b&bookingId=booking-4b',
    )
  })

  it('never offers Mark complete or Mark appeared for a booked slot to a doctor, started or not', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2020-01-01',
      slots: [
        {
          slotId: 'slot-4c',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-4c', patientId: 'patient-4c', patientName: 'Started Patient' },
        },
      ],
    })
    renderWithSession('Doctor')

    await screen.findByText('Started Patient')
    expect(screen.queryByRole('link', { name: /^complete$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^appeared$/i })).not.toBeInTheDocument()
  })

  // real-bug-fix 2026-09-17 (now scoped to APPEARED, its actual successor state): mirrors
  // SlotCompletionService's own "not before scheduledStart" guard client-side - "Mark
  // complete" must not sit there as a live-looking action that only ever bounces back with
  // SLOT_NOT_YET_STARTED once clicked.
  it('hides Mark complete for an appeared slot whose scheduled start is still in the future', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2099-01-01',
      slots: [
        {
          slotId: 'slot-5',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'APPEARED',
          booking: { bookingId: 'booking-5', patientId: 'patient-5', patientName: 'Appeared Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Appeared Patient')
    expect(screen.queryByRole('link', { name: /^complete$/i })).not.toBeInTheDocument()
    expect(screen.getByText(/starts at 09:00/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^appeared$/i })).not.toBeInTheDocument()
  })

  it('offers Mark complete for an appeared slot once its scheduled start has passed', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2020-01-01',
      slots: [
        {
          slotId: 'slot-6',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'APPEARED',
          booking: { bookingId: 'booking-6', patientId: 'patient-6', patientName: 'Started Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Started Patient')
    expect(screen.getByRole('link', { name: /^complete$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/sessions/session-1/operations?slotId=slot-6&bookingId=booking-6',
    )
  })

  it('offers Mark appeared (the correction action) for a no-show slot', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-7',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'NO_SHOW',
          booking: { bookingId: 'booking-7', patientId: 'patient-7', patientName: 'No-Show Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('No-Show Patient')
    expect(screen.getByRole('link', { name: /^appeared$/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/sessions/session-1/operations?slotId=slot-7&bookingId=booking-7',
    )
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  // 057-day-sheet-status-overhaul FR-007: a Doctor caller never sees the "Mark appeared"
  // action, for a BOOKED or a NO_SHOW row - it stays ClinicAdmin/Operations-only.
  it('hides Mark appeared entirely for a Doctor caller, on both booked and no-show rows', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-8',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-8', patientId: 'patient-8', patientName: 'Booked Patient' },
        },
        {
          slotId: 'slot-9',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'NO_SHOW',
          booking: { bookingId: 'booking-9', patientId: 'patient-9', patientName: 'No-Show Patient' },
        },
      ],
    })
    renderWithSession('Doctor')

    await screen.findByText('Booked Patient')
    expect(screen.queryByRole('link', { name: /^appeared$/i })).not.toBeInTheDocument()
  })

  it('still offers Mark complete for a Doctor caller on their own appeared slot', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2020-01-01',
      slots: [
        {
          slotId: 'slot-10',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'APPEARED',
          booking: { bookingId: 'booking-10', patientId: 'patient-10', patientName: 'Doctor View Patient' },
        },
      ],
    })
    renderWithSession('Doctor')

    expect(await screen.findByRole('link', { name: /^complete$/i })).toBeInTheDocument()
  })

  // 057-day-sheet-status-overhaul US3 (FR-009/FR-011): checkboxes only for BOOKED/APPEARED
  // rows with a real booking - OPEN and COMPLETED rows offer none.
  it('offers a selection checkbox only for booked/appeared rows, not open or completed ones', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      sessionDate: '2020-01-01',
      slots: [
        {
          slotId: 'slot-11',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'OPEN',
          booking: null,
        },
        {
          slotId: 'slot-12',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'COMPLETED',
          booking: { bookingId: 'booking-12', patientId: 'patient-12', patientName: 'Completed Patient' },
        },
        {
          slotId: 'slot-13',
          startTime: '09:30:00',
          endTime: '09:45:00',
          tokenNumber: null,
          status: 'APPEARED',
          booking: { bookingId: 'booking-13', patientId: 'patient-13', patientName: 'Appeared Patient' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Appeared Patient')
    // Header "select all" checkbox + exactly one row checkbox (the APPEARED row).
    expect(screen.getAllByRole('checkbox')).toHaveLength(2)
  })

  it('selecting a slot and using "select all" both drive the batch-cancel bar', async () => {
    const user = userEvent.setup()
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-14',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-14', patientId: 'patient-14', patientName: 'Patient One' },
        },
        {
          slotId: 'slot-15',
          startTime: '09:15:00',
          endTime: '09:30:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-15', patientId: 'patient-15', patientName: 'Patient Two' },
        },
      ],
    })
    renderWithSession()

    await screen.findByText('Patient One')
    expect(screen.queryByText(/selected/i)).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: /select 09:00–09:15 for cancellation/i }))
    expect(screen.getByText('1 slot selected')).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: /select all eligible slots/i }))
    expect(screen.getByText('2 slots selected')).toBeInTheDocument()
  })

  it('shows no checkbox or batch-cancel UI at all for a Doctor caller', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      slots: [
        {
          slotId: 'slot-16',
          startTime: '09:00:00',
          endTime: '09:15:00',
          tokenNumber: null,
          status: 'BOOKED',
          booking: { bookingId: 'booking-16', patientId: 'patient-16', patientName: 'Doctor Checkbox Patient' },
        },
      ],
    })
    renderWithSession('Doctor')

    await screen.findByText('Doctor Checkbox Patient')
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('renders session-level actions inline, with no separate Danger zone box', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    renderWithSession()

    // 063-front-desk-walk-in (FR-020): the walk-in action now opens the front-desk screen.
    expect(await screen.findByRole('link', { name: /register walk-in/i })).toBeInTheDocument()
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

  // 065-phase1-stabilization (FR-009): an empty session is cancellable - no client-side block.
  it('lets an empty session be cancelled through the confirm step and the API', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(baseDaySheet)
    mockedCancelSession.mockResolvedValueOnce({ sessionId: 'session-1', bookingsCancelled: 0 })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('button', { name: /cancel entire session/i }))
    expect(screen.getByText(/are you sure/i)).toBeInTheDocument()

    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, cancelled: true })
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByText(/no active bookings needed cancelling/i)).toBeInTheDocument()
    expect(mockedCancelSession).toHaveBeenCalledTimes(1)
  })

  // 065-phase1-stabilization (owner decision 3): a cancelled session says so, and offers nothing
  // that the server would refuse.
  it('shows a whole-cancelled session as cancelled and hides every booking and cancel action', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      cancelled: true,
      slots: [{ ...bookedSlot, slotId: 'slot-open', status: 'OPEN' as const, booking: null }],
    })
    renderWithSession()

    expect(await screen.findByText(/this session is cancelled/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^book$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /register walk-in/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancel entire session/i })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/cancel slots from/i)).not.toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
  })

  it('lists cancelled ranges and hides Book only on slots inside one', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      cancelledRanges: [
        { fromTime: '11:00:00', toTime: '12:00:00' },
        { fromTime: '15:00:00', toTime: null },
      ],
      slots: [
        { ...bookedSlot, slotId: 'slot-before', startTime: '10:45:00', endTime: '11:00:00', status: 'OPEN' as const, booking: null },
        { ...bookedSlot, slotId: 'slot-inside', startTime: '11:00:00', endTime: '11:15:00', status: 'OPEN' as const, booking: null },
        { ...bookedSlot, slotId: 'slot-after', startTime: '12:00:00', endTime: '12:15:00', status: 'OPEN' as const, booking: null },
      ],
    })
    renderWithSession()

    expect(await screen.findByText(/cancelled 11:00–12:00 and from 15:00/i)).toBeInTheDocument()
    const bookLinks = screen.getAllByRole('link', { name: /^book$/i })
    expect(bookLinks.map((l) => l.getAttribute('href'))).toEqual([
      '/staff/clinics/clinic-1/slots/slot-before/book?doctorProfileId=doctor-1',
      '/staff/clinics/clinic-1/slots/slot-after/book?doctorProfileId=doctor-1',
    ])
    expect(screen.getAllByText('Cancelled')).toHaveLength(1)
    // A range leaves the rest of the session in service - the session-level actions stay.
    expect(screen.getByRole('button', { name: /cancel entire session/i })).toBeInTheDocument()
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

// 063-front-desk-walk-in US3/US4 (FR-019, FR-020, tasks.md T029/T031)
describe('SessionSlotsView - walk-ins (063-front-desk-walk-in)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedGetDaySheet.mockReset()
  })

  const timed = {
    slotId: 'slot-t1',
    startTime: '09:00:00',
    endTime: '09:15:00',
    tokenNumber: null,
    status: 'BOOKED' as const,
    appearedAt: null,
    completedAt: null,
    booking: { bookingId: 'b-t1', patientId: 'p-t1', patientName: 'Booked Patient', isWalkIn: false, visitReason: null, visitReasonDetail: null },
  }
  const walkIn = {
    slotId: 'slot-w1',
    startTime: null,
    endTime: null,
    tokenNumber: 1,
    status: 'BOOKED' as const,
    appearedAt: null,
    completedAt: null,
    booking: { bookingId: 'b-w1', patientId: 'p-w1', patientName: 'Asha Rao', isWalkIn: true, visitReason: 'PAIN' as const, visitReasonDetail: null },
  }

  it('shows a Fixed-Time session\u2019s walk-ins in their own walk-in line, with badge and reason, not in the timed list', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, slots: [timed, walkIn] })
    renderWithSession()

    const line = await screen.findByRole('region', { name: /walk-in line/i })
    expect(within(line).getByText('W1')).toBeInTheDocument()
    expect(within(line).getByText('Asha Rao')).toBeInTheDocument()
    expect(within(line).getByText('Pain')).toBeInTheDocument()
    const table = screen.getByRole('table')
    expect(within(table).queryByText('Asha Rao')).not.toBeInTheDocument()
    expect(within(table).getByText('Booked Patient')).toBeInTheDocument()
  })

  it('shows a Queue walk-in inline with its walk-in badge and reason', async () => {
    mockedGetDaySheet.mockResolvedValueOnce({
      ...baseDaySheet,
      mode: 'QUEUE',
      slots: [{ ...walkIn, booking: { ...walkIn.booking, visitReason: 'OTHER' as const, visitReasonDetail: 'Dizziness' } }],
    })
    renderWithSession()

    const table = await screen.findByRole('table')
    expect(within(table).getByText('Asha Rao')).toBeInTheDocument()
    expect(within(table).getByText('Walk-in')).toBeInTheDocument()
    expect(within(table).getByText('Other: Dizziness')).toBeInTheDocument()
  })

  it.each(['FIXED_TIME', 'QUEUE'] as const)('links the %s session\u2019s walk-in button to the front-desk screen with the session pre-selected', async (mode) => {
    mockedGetDaySheet.mockResolvedValueOnce({ ...baseDaySheet, mode, slots: [] })
    renderWithSession()

    expect(await screen.findByRole('link', { name: /register walk-in/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/walk-in?sessionId=session-1',
    )
  })
})

// 064-queue-send-in-complete (FR-006, tasks.md T010a): queue tokens are now minted BOOKED, so the
// Day Sheet's existing status-driven row actions apply to them unchanged.
describe('SessionSlotsView - queue send-in / complete (064-queue-send-in-complete)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedGetDaySheet.mockReset()
  })

  const token = (n: number, status: 'BOOKED' | 'APPEARED', name: string) => ({
    slotId: `slot-q${n}`,
    startTime: null,
    endTime: null,
    tokenNumber: n,
    status,
    appearedAt: status === 'APPEARED' ? '2026-09-24T09:05:00' : null,
    completedAt: null,
    booking: { bookingId: `b-q${n}`, patientId: `p-q${n}`, patientName: name, isWalkIn: false, visitReason: null, visitReasonDetail: null },
  })
  const queueSheet = { ...baseDaySheet, mode: 'QUEUE' as const, slots: [token(1, 'APPEARED', 'In With Doctor'), token(2, 'BOOKED', 'Still Waiting')] }

  it('offers Appeared and Complete on a waiting token, and Complete on an in-with-doctor token, to staff', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(queueSheet)
    renderWithSession()

    const waitingRow = (await screen.findByText('Still Waiting')).closest('tr') as HTMLElement
    expect(within(waitingRow).getByRole('link', { name: /^appeared$/i })).toBeInTheDocument()
    expect(within(waitingRow).getByRole('link', { name: /^complete$/i })).toBeInTheDocument()
    const inRow = screen.getByText('In With Doctor').closest('tr') as HTMLElement
    expect(within(inRow).getByRole('link', { name: /^complete$/i })).toBeInTheDocument()
    expect(within(inRow).queryByRole('link', { name: /^appeared$/i })).not.toBeInTheDocument()
  })

  it('never offers Appeared to a Doctor, who can complete only the token in with them', async () => {
    mockedGetDaySheet.mockResolvedValueOnce(queueSheet)
    renderWithSession('Doctor')

    await screen.findByText('Still Waiting')
    expect(screen.queryByRole('link', { name: /^appeared$/i })).not.toBeInTheDocument()
    const completes = screen.getAllByRole('link', { name: /^complete$/i })
    expect(completes).toHaveLength(1)
    expect(screen.getByText('In With Doctor').closest('tr')).toContainElement(completes[0])
  })
})
