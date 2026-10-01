import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClinicShell } from '../../../src/routes/staff/ClinicShell'
import { ClinicToolsDashboard } from '../../../src/routes/staff/ClinicToolsDashboard'
import {
  ClinicLimitOverridePage,
  FrontDeskWalkInRoutePage,
  OnboardStaffPage,
  ProtectionFlagsPage,
} from '../../../src/routes/staff/ClinicToolPages'
import { listMyClinics, type ClinicMembership, type MyClinicsListResult } from '../../../src/features/staff-clinics/api'
import { getTodayStats, listSessions } from '../../../src/features/day-sheet/api'
import { listInboxItems } from '../../../src/features/inbox/api'
import { getWaitlistCount } from '../../../src/features/waitlist/api'
import { storeStaffSession } from '../../../src/features/staff-login/token'

vi.mock('../../../src/features/staff-clinics/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/staff-clinics/api')>(
    '../../../src/features/staff-clinics/api',
  )
  return { ...actual, listMyClinics: vi.fn() }
})
vi.mock('../../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/day-sheet/api')>(
    '../../../src/features/day-sheet/api',
  )
  return { ...actual, listSessions: vi.fn(), getTodayStats: vi.fn() }
})
vi.mock('../../../src/features/inbox/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/inbox/api')>('../../../src/features/inbox/api')
  return { ...actual, listInboxItems: vi.fn() }
})
vi.mock('../../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/waitlist/api')>(
    '../../../src/features/waitlist/api',
  )
  return { ...actual, getWaitlistCount: vi.fn() }
})
// The restricted forms themselves are already tested; here only whether they render matters.
vi.mock('../../../src/features/staff-onboarding/OnboardStaffForm', () => ({
  OnboardStaffForm: () => <form aria-label="Onboard staff form" />,
}))
vi.mock('../../../src/features/clinic-protection/ProtectionFlagsList', () => ({
  ProtectionFlagsList: () => <section aria-label="Protection flags" />,
}))
vi.mock('../../../src/features/clinic-protection/ClinicLimitOverrideForm', () => ({
  ClinicLimitOverrideForm: () => <form aria-label="Limit override form" />,
}))
vi.mock('../../../src/features/front-desk-walk-in/FrontDeskWalkInPage', () => ({
  FrontDeskWalkInPage: () => <form aria-label="Walk-in form" />,
}))

const mockedListMyClinics = vi.mocked(listMyClinics)

// 073-role-aware-clinic-tools (live-audit finding 7): dashboard tiles and direct URLs follow the
// caller's roles at the clinic in the URL - the same rules the sidebar and the backend apply.

function membership(clinicId: string, role: ClinicMembership['role']): ClinicMembership {
  return { clinicId, name: `Clinic ${clinicId}`, address: '1 Road', role }
}

function memberships(...rows: ClinicMembership[]): MyClinicsListResult {
  return { clinics: rows, page: 0, pageSize: 200, totalCount: rows.length }
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/staff/clinics/:clinicId" element={<ClinicShell />}>
          <Route index element={<ClinicToolsDashboard />} />
          <Route path="onboard" element={<OnboardStaffPage />} />
          <Route path="protection" element={<ProtectionFlagsPage />} />
          <Route path="protection/limit-override" element={<ClinicLimitOverridePage />} />
          <Route path="walk-in" element={<FrontDeskWalkInRoutePage />} />
        </Route>
        <Route path="/test/switch" element={<Link to="/staff/clinics/clinic-b">go to b</Link>} />
      </Routes>
    </MemoryRouter>,
  )
}

function tile(name: string) {
  return screen.queryByRole('heading', { level: 3, name })
}

describe('role-aware clinic tools', () => {
  beforeEach(() => {
    sessionStorage.clear()
    storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'staff@clinic.example' })
    mockedListMyClinics.mockReset()
    vi.mocked(listSessions).mockReset().mockResolvedValue({ sessions: [], doctors: [], page: 0, pageSize: 10, totalCount: 0 })
    vi.mocked(getTodayStats).mockReset().mockResolvedValue({ completedCount: 0, noShowCount: 0 })
    vi.mocked(listInboxItems).mockReset().mockResolvedValue([])
    vi.mocked(getWaitlistCount).mockReset().mockResolvedValue({ waitingCount: 0 })
  })

  describe('dashboard tiles', () => {
    it.each(['Doctor', 'Operations'] as const)('hides the admin-only tiles from %s', async (role) => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', role)))
      renderAt('/staff/clinics/clinic-a')

      expect(await screen.findByRole('heading', { level: 3, name: 'Day sheet' })).toBeInTheDocument()
      await screen.findByText('Clinic clinic-a')
      expect(tile('Onboard staff')).not.toBeInTheDocument()
      expect(tile('Booking protection')).not.toBeInTheDocument()
    })

    it('shows them to a ClinicAdmin', async () => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', 'ClinicAdmin')))
      renderAt('/staff/clinics/clinic-a')

      expect(await screen.findByRole('heading', { level: 3, name: 'Onboard staff' })).toBeInTheDocument()
      expect(tile('Booking protection')).toBeInTheDocument()
    })

    it.each([
      ['Doctor row first', ['Doctor', 'ClinicAdmin']],
      ['ClinicAdmin row first', ['ClinicAdmin', 'Doctor']],
    ] as const)('uses every role a multi-role user holds (%s)', async (_label, roles) => {
      mockedListMyClinics.mockResolvedValue(memberships(...roles.map((r) => membership('clinic-a', r))))
      renderAt('/staff/clinics/clinic-a')

      expect(await screen.findByRole('heading', { level: 3, name: 'Onboard staff' })).toBeInTheDocument()
      expect(tile('Booking protection')).toBeInTheDocument()
    })

    it('shows no restricted tile while roles are loading', async () => {
      mockedListMyClinics.mockReturnValue(new Promise(() => {}))
      renderAt('/staff/clinics/clinic-a')

      expect(await screen.findByRole('heading', { level: 3, name: 'Day sheet' })).toBeInTheDocument()
      expect(tile('Onboard staff')).not.toBeInTheDocument()
      expect(tile('Booking protection')).not.toBeInTheDocument()
    })
  })

  describe('direct URLs', () => {
    it.each([
      ['/onboard', 'Onboard staff form'],
      ['/protection', 'Protection flags'],
      ['/protection/limit-override', 'Limit override form'],
      ['/walk-in', 'Walk-in form'],
    ])('a Doctor opening %s gets an explanation, never the form', async (path, formName) => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', 'Doctor')))
      renderAt(`/staff/clinics/clinic-a${path}`)

      expect(await screen.findByText(/isn't available for your role at this clinic/i)).toBeInTheDocument()
      expect(screen.queryByLabelText(formName)).not.toBeInTheDocument()
      expect(screen.getByRole('link', { name: /back to the clinic dashboard/i })).toHaveAttribute(
        'href',
        '/staff/clinics/clinic-a',
      )
    })

    it('Operations can open walk-in', async () => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', 'Operations')))
      renderAt('/staff/clinics/clinic-a/walk-in')
      expect(await screen.findByLabelText('Walk-in form')).toBeInTheDocument()
    })

    it('Operations opening onboarding gets the explanation', async () => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', 'Operations')))
      renderAt('/staff/clinics/clinic-a/onboard')
      expect(await screen.findByText(/only clinic administrators/i)).toBeInTheDocument()
      expect(screen.queryByLabelText('Onboard staff form')).not.toBeInTheDocument()
    })

    it.each([
      ['/onboard', 'Onboard staff form'],
      ['/protection', 'Protection flags'],
      ['/protection/limit-override', 'Limit override form'],
      ['/walk-in', 'Walk-in form'],
    ])('a ClinicAdmin opening %s gets the tool', async (path, formName) => {
      mockedListMyClinics.mockResolvedValue(memberships(membership('clinic-a', 'ClinicAdmin')))
      renderAt(`/staff/clinics/clinic-a${path}`)
      expect(await screen.findByLabelText(formName)).toBeInTheDocument()
    })

    it('shows a loading state, not the form, while roles resolve', async () => {
      let resolve: (value: MyClinicsListResult) => void = () => {}
      mockedListMyClinics.mockReturnValue(new Promise((r) => (resolve = r)))
      renderAt('/staff/clinics/clinic-a/onboard')

      expect(await screen.findByText(/checking your access/i)).toBeInTheDocument()
      expect(screen.queryByLabelText('Onboard staff form')).not.toBeInTheDocument()

      await act(async () => resolve(memberships(membership('clinic-a', 'ClinicAdmin'))))
      expect(await screen.findByLabelText('Onboard staff form')).toBeInTheDocument()
    })

    it('does not show the form when the role lookup fails', async () => {
      mockedListMyClinics.mockRejectedValue(new Error('offline'))
      renderAt('/staff/clinics/clinic-a/onboard')

      expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't confirm your access/i)
      expect(screen.queryByLabelText('Onboard staff form')).not.toBeInTheDocument()
    })
  })

  it("switching clinics uses the new clinic's roles, never the old clinic's", async () => {
    const user = userEvent.setup()
    mockedListMyClinics.mockResolvedValue(
      memberships(membership('clinic-a', 'ClinicAdmin'), membership('clinic-b', 'Doctor')),
    )
    render(
      <MemoryRouter initialEntries={['/staff/clinics/clinic-a']}>
        <Routes>
          <Route path="/staff/clinics/:clinicId" element={<ClinicShell />}>
            <Route
              index
              element={
                <>
                  <ClinicToolsDashboard />
                  <Link to="/staff/clinics/clinic-b">switch to b</Link>
                </>
              }
            />
          </Route>
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { level: 3, name: 'Onboard staff' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'switch to b' }))

    // Immediately after the switch - before clinic B's lookup resolves - A's admin rights must not show.
    expect(tile('Onboard staff')).not.toBeInTheDocument()
    await screen.findByText('Clinic clinic-b')
    expect(tile('Onboard staff')).not.toBeInTheDocument()
    expect(tile('Booking protection')).not.toBeInTheDocument()
  })
})
