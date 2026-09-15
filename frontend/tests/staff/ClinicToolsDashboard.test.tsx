import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClinicToolsDashboard } from '../../src/routes/staff/ClinicToolsDashboard'
import { listSessions } from '../../src/features/day-sheet/api'
import { listInboxItems, type InboxItemResponse } from '../../src/features/inbox/api'
import { getWaitlistCount } from '../../src/features/waitlist/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>(
    '../../src/features/day-sheet/api',
  )
  return { ...actual, listSessions: vi.fn() }
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
    expect(mockedListSessions).toHaveBeenCalledWith('clinic-1', 'staff-jwt', { from: today, to: today, page: 0, size: 1 })
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
})
