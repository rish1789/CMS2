import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DaySheet } from '../../src/features/day-sheet/DaySheet'
import { listSessions, type SessionSummary } from '../../src/features/day-sheet/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>(
    '../../src/features/day-sheet/api',
  )
  return { ...actual, listSessions: vi.fn() }
})

const mockedListSessions = vi.mocked(listSessions)

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/day-sheet']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/day-sheet" element={<DaySheet />} />
        <Route path="/staff/clinics/:clinicId/day-sheet/:sessionId" element={<div>Session detail</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

// The arrow "Open ..." link is always present in every row regardless of whether the Doctor
// column or the merged single-doctor tab is showing - the one query safe to rely on generically.
function findOpenLink(doctorName: string) {
  return screen.findByRole('link', { name: new RegExp(`^Open ${doctorName.replace('.', '\\.')}'s session`, 'i') })
}

const SESSION_A: SessionSummary = {
  sessionId: 'session-1',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Priya Nair',
  sessionDate: '2026-09-10',
  startTime: '09:00:00',
  endTime: '13:00:00',
  mode: 'FIXED_TIME',
  bookedSlotCount: 6,
  totalSlotCount: 10,
}

const SESSION_B: SessionSummary = {
  sessionId: 'session-2',
  doctorProfileId: 'doctor-2',
  doctorName: 'Dr. Arjun Rao',
  sessionDate: '2026-09-11',
  startTime: '14:00:00',
  endTime: '18:00:00',
  mode: 'QUEUE',
  bookedSlotCount: 0,
  totalSlotCount: 0,
}

const DOCTOR_1 = { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001' }
const DOCTOR_2 = { doctorProfileId: 'doctor-2', name: 'Dr. Arjun Rao', staffCode: 'DR-1002' }

describe('DaySheet (041-staff-console-pickers T012/US2, 042-day-sheet-hardening US2)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListSessions.mockReset()
  })

  it('renders a single-doctor page as a merged tab header, no repeated Doctor column, and navigates on click', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    const expectedDate = new Date('2026-09-10T00:00:00').toLocaleDateString(undefined, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    })

    // The doctor's identity appears once, in the tab - not repeated per row.
    expect(await screen.findByText('DR-1001')).toBeInTheDocument()
    expect(screen.getByText('Dr. Priya Nair')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /doctor/i })).not.toBeInTheDocument()

    expect(screen.getByText(expectedDate)).toBeInTheDocument()
    expect(screen.getByText('Fixed-Time')).toBeInTheDocument()
    expect(screen.getByText('6/10')).toBeInTheDocument()

    const openLink = await findOpenLink('Dr. Priya Nair')
    await user.click(openLink)

    await waitFor(() => {
      expect(screen.getByText('Session detail')).toBeInTheDocument()
    })
  })

  it('shows the Doctor column when a page genuinely mixes more than one doctor', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    renderWithSession()

    expect(await screen.findByRole('columnheader', { name: /doctor/i })).toBeInTheDocument()
    // No merged tab in this case - the doctor's staff code isn't rendered as a standalone tab.
    expect(screen.queryByText('DR-1001')).not.toBeInTheDocument()
    const row = (await findOpenLink('Dr. Priya Nair')).closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByText('Dr. Priya Nair')).toBeInTheDocument()
  })

  // staff-console-audit-2026-09-10 P1: the list previously showed only a date, never a time.
  it("shows each session's time range", async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    renderWithSession()

    expect(await screen.findByText('09:00–13:00')).toBeInTheDocument()
    expect(screen.getByText('14:00–18:00')).toBeInTheDocument()
  })

  // staff-console-audit-2026-09-10 P1: today's row is marked, so it isn't visually identical to
  // one two weeks out - uses a fixed system time rather than relying on whatever date the test
  // happens to run on.
  it("marks today's session distinctly from a session on another date", async () => {
    vi.setSystemTime(new Date('2026-09-10T08:00:00'))
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    renderWithSession()

    const todayRow = (await screen.findByText('09:00–13:00')).closest('tr')
    const otherRow = screen.getByText('14:00–18:00').closest('tr')
    expect(within(todayRow as HTMLElement).getByText('Today')).toBeInTheDocument()
    expect(within(otherRow as HTMLElement).queryByText('Today')).not.toBeInTheDocument()

    vi.useRealTimers()
  })

  // staff-console-audit-2026-09-10 P1: the row's hover state previously implied the whole row
  // was clickable when only a 34x24px arrow actually was - it now genuinely is, via a
  // "stretched link" (`relative` on the <tr>, `after:absolute after:inset-0` on the arrow
  // anchor). NOT covered by an automated test here: jsdom has no layout/paint engine, so it
  // never does CSS-based pointer hit-testing against a `::after` pseudo-element - clicking a
  // non-anchor cell in this environment simply does nothing, confirmed empirically (the same
  // class of jsdom gap already documented in this suite for getBoundingClientRect() and
  // <details name>). Verified instead by a direct live-browser check.

  it('does not crash the page when a response is missing the time fields (e.g. a stale backend)', async () => {
    const { startTime: _startTime, endTime: _endTime, ...sessionWithoutTimes } = SESSION_A
    mockedListSessions.mockResolvedValueOnce({
      sessions: [sessionWithoutTimes as SessionSummary],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    renderWithSession()

    expect(await findOpenLink('Dr. Priya Nair')).toBeInTheDocument()
  })

  it('shows an explicit "no slots yet" state instead of a 0-of-0 fraction', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_B],
      doctors: [DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    renderWithSession()

    await findOpenLink('Dr. Arjun Rao')
    expect(screen.getByText(/no slots yet/i)).toBeInTheDocument()
    expect(screen.queryByText('0/0')).not.toBeInTheDocument()
  })

  it('shows a clear empty state when there are no sessions', async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 20, totalCount: 0 })
    renderWithSession()

    expect(await screen.findByText(/no sessions scheduled/i)).toBeInTheDocument()
  })

  it('offers a type-to-filter doctor search sourced from the response doctors field and re-fetches on an exact match', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    renderWithSession()
    await findOpenLink('Dr. Priya Nair')

    const filter = screen.getByLabelText(/filter by doctor/i)

    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    // Typing a partial name shouldn't re-fetch yet - only an exact match should.
    await userEvent.type(filter, 'Dr. Priya')
    expect(mockedListSessions).not.toHaveBeenLastCalledWith(
      'clinic-1',
      'staff-jwt',
      expect.objectContaining({ doctorProfileId: 'doctor-1' }),
    )
    await userEvent.type(filter, ' Nair')

    await waitFor(() => {
      expect(mockedListSessions).toHaveBeenLastCalledWith(
        'clinic-1',
        'staff-jwt',
        expect.objectContaining({ doctorProfileId: 'doctor-1' }),
      )
    })
  })

  it('resolves an exact match by Staff ID (staffCode) as well as by name', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    renderWithSession()
    await findOpenLink('Dr. Priya Nair')

    const filter = screen.getByLabelText(/filter by doctor/i)
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_B],
      doctors: [DOCTOR_1, DOCTOR_2],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    // Typed by Staff ID, not name - a receptionist who only knows the code shouldn't be stuck.
    await userEvent.type(filter, 'DR-1002')

    await waitFor(() => {
      expect(mockedListSessions).toHaveBeenLastCalledWith(
        'clinic-1',
        'staff-jwt',
        expect.objectContaining({ doctorProfileId: 'doctor-2' }),
      )
    })
  })

  it('clearing the doctor search resets to All doctors', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    renderWithSession()
    await findOpenLink('Dr. Priya Nair')

    const filter = screen.getByLabelText(/filter by doctor/i)
    // Typing the exact name triggers one fetch (filtered)...
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    await userEvent.type(filter, 'Dr. Priya Nair')
    await waitFor(() => {
      expect(mockedListSessions).toHaveBeenLastCalledWith(
        'clinic-1',
        'staff-jwt',
        expect.objectContaining({ doctorProfileId: 'doctor-1' }),
      )
    })

    // ...and clearing it triggers a second, separate fetch back to "All doctors".
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A, SESSION_B],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    await userEvent.clear(filter)

    await waitFor(() => {
      expect(mockedListSessions).toHaveBeenLastCalledWith(
        'clinic-1',
        'staff-jwt',
        expect.objectContaining({ doctorProfileId: undefined }),
      )
    })
  })

  it('paging forward re-fetches the next page rather than reusing already-fetched data', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_A],
      doctors: [DOCTOR_1],
      page: 0,
      pageSize: 1,
      totalCount: 2,
    })
    renderWithSession()
    await findOpenLink('Dr. Priya Nair')

    mockedListSessions.mockResolvedValueOnce({
      sessions: [SESSION_B],
      doctors: [DOCTOR_1],
      page: 1,
      pageSize: 1,
      totalCount: 2,
    })
    await userEvent.click(screen.getByRole('button', { name: /^next$/i }))

    expect(await findOpenLink('Dr. Arjun Rao')).toBeInTheDocument()
    expect(screen.queryByText('Dr. Priya Nair')).not.toBeInTheDocument()
    expect(mockedListSessions).toHaveBeenLastCalledWith('clinic-1', 'staff-jwt', expect.objectContaining({ page: 1 }))
  })
})
