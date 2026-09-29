import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useOutletContext } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClinicShell, type ClinicShellOutletContext } from '../../src/routes/staff/ClinicShell'
import { listMyClinics } from '../../src/features/staff-clinics/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

// 057-day-sheet-status-overhaul: reads the context ClinicShell now provides via <Outlet
// context={...}>, the same way a real routed page (e.g. SessionSlotsView) does.
function OutletRoleProbe() {
  const { role } = useOutletContext<ClinicShellOutletContext>()
  return <div>resolved role: {role ?? 'none'}</div>
}

vi.mock('../../src/features/staff-clinics/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-clinics/api')>(
    '../../src/features/staff-clinics/api',
  )
  return { ...actual, listMyClinics: vi.fn() }
})

const mockedListMyClinics = vi.mocked(listMyClinics)

function renderShell(initialEntry = '/staff/clinics/clinic-1') {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/staff/clinics/:clinicId" element={<ClinicShell />}>
          <Route index element={<div>outlet content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

// 056-design-copy-quality-pass: ClinicShell used to nest its sidebar inside StaffShell's own
// mx-auto max-w-5xl via a plain `flex gap-6` - the middle layer of the three nested centered
// boxes that caused the wasted-space complaint. The sidebar is now its own sm:sticky column and
// the content pane carries one explicit max-w-4xl cap instead (research.md Decisions 1-2).
describe('ClinicShell', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListMyClinics.mockReset()
    storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  })

  it('renders the sidebar nav landmark and the routed outlet content side by side', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '1 Main St', role: 'ClinicAdmin' }],
      page: 0,
      pageSize: 200,
      totalCount: 1,
    })
    renderShell()

    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument()
    expect(screen.getByText('outlet content')).toBeInTheDocument()
  })

  it('the content pane carries one explicit reading-width cap, not the old nested-centering wrapper', () => {
    mockedListMyClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 200, totalCount: 0 })
    renderShell()

    const outlet = screen.getByText('outlet content')
    const contentPane = outlet.closest('.max-w-4xl')
    expect(contentPane).not.toBeNull()
    expect(contentPane).toHaveClass('flex-1')
  })

  it('the sidebar column is sticky from sm: up, not a plain flex item', () => {
    mockedListMyClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 200, totalCount: 0 })
    renderShell()

    const sidebarNav = screen.getByRole('navigation', { name: 'Primary' })
    const stickyWrapper = sidebarNav.closest('.sm\\:sticky')
    expect(stickyWrapper).not.toBeNull()
  })

  it('shows the raw clinicId as a fallback breadcrumb before the membership lookup resolves, then the resolved name', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '1 Main St', role: 'ClinicAdmin' }],
      page: 0,
      pageSize: 200,
      totalCount: 2,
    })
    renderShell()

    expect(screen.getByText('clinic-1')).toBeInTheDocument()
    expect(await screen.findByText('Sunrise Clinic')).toBeInTheDocument()
  })

  it('shows "Switch clinic" only when the caller has more than one clinic membership', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '1 Main St', role: 'ClinicAdmin' }],
      page: 0,
      pageSize: 200,
      totalCount: 1,
    })
    renderShell()

    await screen.findByText('Sunrise Clinic')
    expect(screen.queryByRole('link', { name: 'Switch clinic' })).not.toBeInTheDocument()
  })

  it('hides "Onboard staff" for a non-ClinicAdmin role, resolved from the membership lookup', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '1 Main St', role: 'Doctor' }],
      page: 0,
      pageSize: 200,
      totalCount: 1,
    })
    renderShell()

    await screen.findByText('Sunrise Clinic')
    expect(screen.queryByRole('link', { name: /Onboard staff/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Day sheet/ })).toBeInTheDocument()
  })

  it('provides the resolved role to routed child pages via Outlet context', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '1 Main St', role: 'Doctor' }],
      page: 0,
      pageSize: 200,
      totalCount: 1,
    })
    render(
      <MemoryRouter initialEntries={['/staff/clinics/clinic-1']}>
        <Routes>
          <Route path="/staff/clinics/:clinicId" element={<ClinicShell />}>
            <Route index element={<OutletRoleProbe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('resolved role: Doctor')).toBeInTheDocument()
  })
})
