import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClinicToolsDashboard } from '../../src/routes/staff/ClinicToolsDashboard'
import { listSessions, getTodayStats } from '../../src/features/day-sheet/api'
import { listInboxItems, type InboxItemResponse } from '../../src/features/inbox/api'
import { getWaitlistCount } from '../../src/features/waitlist/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>(
    '../../src/features/day-sheet/api',
  )
  return { ...actual, listSessions: vi.fn(), getTodayStats: vi.fn() }
})

vi.mock('../../src/features/inbox/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/inbox/api')>('../../src/features/inbox/api')
  return { ...actual, listInboxItems: vi.fn() }
})

vi.mock('../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/waitlist/api')>(
    '../../src/features/waitlist/api',
  )
  return { ...actual, getWaitlistCount: vi.fn() }
})

const mockedListSessions = vi.mocked(listSessions)
const mockedListInboxItems = vi.mocked(listInboxItems)
const mockedGetWaitlistCount = vi.mocked(getWaitlistCount)
const mockedGetTodayStats = vi.mocked(getTodayStats)

function inboxItem(status: InboxItemResponse['status']): InboxItemResponse {
  return {
    id: `item-${status}`,
    itemType: 'WALK_IN',
    status,
    claimedByAccountId: null,
    claimedByName: null,
    createdAt: '2026-09-10T10:00:00Z',
    summary: {},
  }
}

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId" element={<ClinicToolsDashboard />} />
      </Routes>
    </MemoryRouter>,
  )
}

// dashboard-live-data-2026-09-10: the audit's own P2 finding - "the dashboard is a table of
// contents, not an operational home... zero live data". These tests cover the three tiles that
// now fetch real counts instead of showing only static prose.
describe('ClinicToolsDashboard live data', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListSessions.mockReset()
    mockedListInboxItems.mockReset()
    mockedGetWaitlistCount.mockReset()
    mockedGetTodayStats.mockReset()
    // Sensible default so every pre-existing test (which doesn't care about this tile) doesn't
    // need its own mock - tests that do care override with mockResolvedValueOnce before rendering.
    mockedGetTodayStats.mockResolvedValue({ completedCount: 0, noShowCount: 0 })
  })

  it("shows today's session count on the Day sheet tile", async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 1, totalCount: 4 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText('4 sessions today')).toBeInTheDocument()
    // Local date components, matching the component's own todayIsoDate() - not
    // toISOString().slice(0, 10), which is UTC and would mismatch near a local midnight.
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    // 051-staff-dashboard-enhancement US1: size bumped from 1 (count-only) to a real page size -
    // the dashboard now also renders the actual session rows, not just the total.
    expect(mockedListSessions).toHaveBeenCalledWith('clinic-1', 'staff-jwt', { from: today, to: today, page: 0, size: 10 })
  })

  it("renders today's sessions with doctor, time, mode, and slot-fill, linking into Day Sheet", async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [
        {
          sessionId: 'session-1',
          doctorProfileId: 'doctor-1',
          doctorName: 'Dr. Mehta',
          sessionDate: '2026-09-15',
          startTime: '09:00:00',
          endTime: '11:00:00',
          mode: 'FIXED_TIME',
          bookedSlotCount: 3,
          totalSlotCount: 8,
        },
      ],
      doctors: [],
      page: 0,
      pageSize: 10,
      totalCount: 1,
    })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText('Dr. Mehta')).toBeInTheDocument()
    expect(screen.getByText('09:00–11:00')).toBeInTheDocument()
    expect(screen.getByText('3/8 booked')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Dr\. Mehta/ })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/day-sheet/session-1',
    )
  })

  it("shows an empty state when there are no sessions today", async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 10, totalCount: 0 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText('No sessions scheduled today.')).toBeInTheDocument()
  })

  it('shows a "+N more in Day Sheet" note when more sessions exist than are shown', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [
        {
          sessionId: 'session-1',
          doctorProfileId: 'doctor-1',
          doctorName: 'Dr. Mehta',
          sessionDate: '2026-09-15',
          startTime: '09:00:00',
          endTime: '11:00:00',
          mode: 'FIXED_TIME',
          bookedSlotCount: 3,
          totalSlotCount: 8,
        },
      ],
      doctors: [],
      page: 0,
      pageSize: 10,
      totalCount: 12,
    })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText('+11 more in Day Sheet')).toBeInTheDocument()
  })

  it('shows the unclaimed inbox count as a badge, and "all caught up" copy at zero', async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 1, totalCount: 0 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText("You're all caught up.")).toBeInTheDocument()
    expect(screen.queryByText(/unclaimed/i)).not.toBeInTheDocument()
  })

  it('shows a non-zero unclaimed inbox count as a badge', async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 1, totalCount: 0 })
    mockedListInboxItems.mockResolvedValueOnce([inboxItem('UNCLAIMED'), inboxItem('UNCLAIMED'), inboxItem('CLAIMED')])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    renderWithSession()

    expect(await screen.findByText('2 unclaimed')).toBeInTheDocument()
  })

  it('shows the waitlist backlog count on the join-waitlist tile', async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 1, totalCount: 0 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 3 })
    renderWithSession()

    expect(await screen.findByText('3 waiting')).toBeInTheDocument()
  })

  it('one failing count never blocks the others from rendering', async () => {
    mockedListSessions.mockRejectedValueOnce(new Error('network error'))
    mockedListInboxItems.mockResolvedValueOnce([inboxItem('UNCLAIMED')])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 5 })
    renderWithSession()

    expect(await screen.findByText('5 waiting')).toBeInTheDocument()
    expect(screen.getByText('1 unclaimed')).toBeInTheDocument()
    expect(screen.queryByText(/sessions? today/)).not.toBeInTheDocument()
  })

  it("shows today's real completed and no-show counts", async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 10, totalCount: 0 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    mockedGetTodayStats.mockResolvedValueOnce({ completedCount: 3, noShowCount: 1 })
    renderWithSession()

    // The tile's labels render immediately (with a loading skeleton), so waiting for a label
    // does not mean the stats have arrived - wait for the counts themselves.
    expect(await screen.findByText('Completed today')).toBeInTheDocument()
    expect(await screen.findByText('3')).toBeInTheDocument()
    expect(screen.getByText('No-shows today')).toBeInTheDocument()
    expect(await screen.findByText('1')).toBeInTheDocument()
  })

  it("a failed today's-stats fetch never blocks the other tiles from rendering", async () => {
    mockedListSessions.mockResolvedValueOnce({ sessions: [], doctors: [], page: 0, pageSize: 10, totalCount: 2 })
    mockedListInboxItems.mockResolvedValueOnce([])
    mockedGetWaitlistCount.mockResolvedValueOnce({ waitingCount: 0 })
    mockedGetTodayStats.mockReset()
    mockedGetTodayStats.mockRejectedValueOnce(new Error('network error'))
    renderWithSession()

    expect(await screen.findByText('2 sessions today')).toBeInTheDocument()
    expect(screen.getByText('Completed today')).toBeInTheDocument()
  })
})
