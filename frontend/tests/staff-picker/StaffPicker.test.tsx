import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StaffPicker } from '../../src/features/staff-picker/StaffPicker'
import { listClinicStaff, type ListClinicStaffParams, type StaffSummary } from '../../src/features/staff-picker/api'
import { deactivateStaff } from '../../src/features/staff-onboarding/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/staff-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-picker/api')>(
    '../../src/features/staff-picker/api',
  )
  return { ...actual, listClinicStaff: vi.fn() }
})

vi.mock('../../src/features/staff-onboarding/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-onboarding/api')>(
    '../../src/features/staff-onboarding/api',
  )
  return { ...actual, deactivateStaff: vi.fn() }
})

const mockedListClinicStaff = vi.mocked(listClinicStaff)
const mockedDeactivateStaff = vi.mocked(deactivateStaff)

// pagination-unification-2026-09-10: search/role/status/specialization filtering, sorting, and
// paging all moved server-side, so every one of those interactions now issues a real (mocked)
// API call instead of re-deriving from one client-held array. Rather than hand-queue a
// mockResolvedValueOnce per interaction, this fake replicates the backend's own filter/sort/
// page contract (RoleAssignmentRepository.search) over an in-memory fixture - the same approach
// as testing against a real paginated endpoint, without a live server.
function installFakeStaffBackend(initialStaff: StaffSummary[]) {
  let staff = [...initialStaff]

  mockedListClinicStaff.mockImplementation(async (_clinicId, _token, params: ListClinicStaffParams = {}) => {
    const { q, role, active, specialization, sortBy, sortDir = 'asc', page = 0, size = 20 } = params
    let filtered = staff.filter((member) => {
      if (role && member.role !== role) return false
      if (active !== undefined && member.active !== active) return false
      if (specialization && member.specialization !== specialization) return false
      if (q) {
        const term = q.toLowerCase()
        if (!member.name.toLowerCase().includes(term) && !member.staffCode.toLowerCase().includes(term)) return false
      }
      return true
    })
    const key = sortBy ?? 'name'
    const dir = sortDir === 'desc' ? -1 : 1
    filtered = [...filtered].sort((a, b) => {
      let result = 0
      if (key === 'name') result = a.name.localeCompare(b.name)
      else if (key === 'experienceYears') result = (a.experienceYears ?? -1) - (b.experienceYears ?? -1)
      else if (key === 'joinedAt') result = new Date(a.joinedAt).getTime() - new Date(b.joinedAt).getTime()
      return dir * result
    })
    const totalCount = filtered.length
    const start = page * size
    const specializations = Array.from(
      new Set(staff.map((member) => member.specialization).filter((s): s is string => Boolean(s))),
    ).sort()
    return { staff: filtered.slice(start, start + size), page, pageSize: size, totalCount, specializations }
  })

  mockedDeactivateStaff.mockImplementation(async (_clinicId, accountId) => {
    staff = staff.map((member) => (member.accountId === accountId ? { ...member, active: false } : member))
    const member = staff.find((s) => s.accountId === accountId)!
    return { accountId, clinicId: 'clinic-1', role: member.role, active: false }
  })
}

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/staff']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/staff" element={<StaffPicker />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('StaffPicker (041-staff-console-pickers T035/US4)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListClinicStaff.mockReset()
    mockedDeactivateStaff.mockReset()
  })

  const TWO_STAFF: StaffSummary[] = [
    {
      roleAssignmentId: 'role-assignment-1',
      accountId: 'account-1',
      name: 'Dr. Sharma',
      staffCode: 'DR-1001',
      role: 'Doctor',
      email: 'dr.sharma@clinic.example',
      mobile: '+91 98765 43210',
      specialization: 'Cardiology',
      experienceYears: 8,
      joinedAt: '2024-01-15T00:00:00Z',
      active: true,
    },
    {
      roleAssignmentId: 'role-assignment-9',
      accountId: 'account-9',
      name: 'Jamie Ops',
      staffCode: 'OP-9001',
      role: 'Operations',
      email: 'jamie.ops@clinic.example',
      mobile: null,
      specialization: null,
      experienceYears: null,
      joinedAt: '2023-06-01T00:00:00Z',
      active: true,
    },
  ]

  it('always shows page index, record range, and page-navigation controls, even on a single page', async () => {
    installFakeStaffBackend(TWO_STAFF)
    renderWithSession()
    await screen.findByText('Dr. Sharma')

    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
    expect(screen.getByText('1-2 of 2 Records')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /previous/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^next$/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/jump to page/i)).toBeInTheDocument()
  })

  it('opens the employee modal on click and shows Info, then completes a deactivation from the Actions tab', async () => {
    installFakeStaffBackend([TWO_STAFF[1]])
    renderWithSession()

    expect(await screen.findByText('Jamie Ops')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Operations' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /jamie ops/i }))
    const dialog = screen.getByRole('dialog')

    // Info tab is the default.
    expect(within(dialog).getByText('OP-9001')).toBeInTheDocument()
    expect(within(dialog).getByText('jamie.ops@clinic.example')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: /actions/i }))
    await userEvent.selectOptions(within(dialog).getByLabelText(/reason/i), 'SERVICE_NOT_REQUIRED')
    await userEvent.click(within(dialog).getByRole('button', { name: /^deactivate$/i }))
    expect(within(dialog).getByText(/are you sure/i)).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: /confirm/i }))

    expect(mockedDeactivateStaff).toHaveBeenCalledWith('clinic-1', 'account-9', 'SERVICE_NOT_REQUIRED', 'staff-jwt')
    expect(await within(dialog).findByText('This employee is already inactive.')).toBeInTheDocument()
  })

  it('shows a clear empty state when there is no active staff', async () => {
    mockedListClinicStaff.mockResolvedValueOnce({ staff: [], page: 0, pageSize: 10, totalCount: 0, specializations: [] })
    renderWithSession()

    expect(await screen.findByText(/no staff match your search/i)).toBeInTheDocument()
  })

  it('filters by search term and role, and reports no matches distinctly from no staff', async () => {
    installFakeStaffBackend(TWO_STAFF)
    renderWithSession()
    expect(await screen.findByText('Dr. Sharma')).toBeInTheDocument()

    // Free-text search is debounced (300ms) - the disappearance/appearance below only settles
    // once that timer fires and the (mocked) request round-trips.
    await userEvent.type(screen.getByLabelText(/search staff/i), 'jamie')
    await waitFor(() => expect(screen.queryByText('Dr. Sharma')).not.toBeInTheDocument())
    expect(screen.getByText('Jamie Ops')).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText(/search staff/i))
    await userEvent.selectOptions(screen.getByLabelText(/filter staff by role/i), 'Doctor')
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeInTheDocument())
    expect(screen.queryByText('Jamie Ops')).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/search staff/i), 'nobody-matches-this')
    expect(await screen.findByText(/no staff match your search/i)).toBeInTheDocument()
  })

  it("blocks deactivation from the modal's Actions tab for the logged-in user's own row", async () => {
    installFakeStaffBackend(TWO_STAFF)
    renderWithSession() // session accountId is 'account-1', matching Dr. Sharma

    await screen.findByText('Dr. Sharma')
    await userEvent.click(screen.getByRole('button', { name: /dr\. sharma/i }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('(You)')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: /actions/i }))
    expect(within(dialog).getByText("You can't deactivate your own account.")).toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/reason/i)).not.toBeInTheDocument()
  })

  it('filters by specialization and sorts by name', async () => {
    installFakeStaffBackend(TWO_STAFF)
    renderWithSession()
    await screen.findByText('Dr. Sharma')

    // Only Doctors have a specialization - the filter should only ever offer real values.
    await userEvent.selectOptions(screen.getByLabelText(/filter staff by specialization/i), 'Cardiology')
    await waitFor(() => expect(screen.queryByText('Jamie Ops')).not.toBeInTheDocument())
    expect(screen.getByText('Dr. Sharma')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText(/filter staff by specialization/i), 'All')
    await screen.findByText('Jamie Ops')

    const rowNames = () => screen.getAllByRole('row').slice(1).map((row) => row.textContent ?? '')
    await userEvent.click(screen.getByRole('button', { name: /sort by name/i }))
    await waitFor(() => expect(rowNames()[0]).toContain('Dr. Sharma')) // ascending: D before J

    await userEvent.click(screen.getByRole('button', { name: /sort by name/i }))
    await waitFor(() => expect(rowNames()[0]).toContain('Jamie Ops')) // second click reverses to descending
  })

  it('defaults to Active and can reveal Inactive/All staff, with Actions disabled for an inactive row', async () => {
    const deactivatedDoctor: StaffSummary = {
      ...TWO_STAFF[0],
      roleAssignmentId: 'role-assignment-2',
      accountId: 'account-2',
      name: 'Dr. Retired',
      active: false,
    }
    installFakeStaffBackend([...TWO_STAFF, deactivatedDoctor])
    renderWithSession()

    await screen.findByText('Dr. Sharma')
    expect(screen.queryByText('Dr. Retired')).not.toBeInTheDocument() // default filter is Active

    await userEvent.selectOptions(screen.getByLabelText(/filter staff by status/i), 'Inactive')
    await waitFor(() => expect(screen.getByText(/Dr\. Retired/)).toBeInTheDocument())
    expect(screen.queryByText('Dr. Sharma')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /dr\. retired/i }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: /actions/i }))
    expect(within(dialog).getByText('This employee is already inactive.')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: /close/i }))

    await userEvent.selectOptions(screen.getByLabelText(/filter staff by status/i), 'All')
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeInTheDocument())
    expect(screen.getByText(/Dr\. Retired/)).toBeInTheDocument()
  })

  it('closes the employee modal on Escape', async () => {
    installFakeStaffBackend([TWO_STAFF[1]])
    renderWithSession()

    await userEvent.click(await screen.findByRole('button', { name: /jamie ops/i }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('paginates at 10 rows per page', async () => {
    const manyStaff: StaffSummary[] = Array.from({ length: 12 }, (_, i) => ({
      roleAssignmentId: `role-assignment-${i}`,
      accountId: `account-${i}`,
      name: `Staff Member ${String(i).padStart(2, '0')}`,
      staffCode: `OP-${1000 + i}`,
      role: 'Operations',
      email: `staff${i}@clinic.example`,
      mobile: null,
      specialization: null,
      experienceYears: null,
      joinedAt: '2024-01-01T00:00:00Z',
      active: true,
    }))
    installFakeStaffBackend(manyStaff)
    renderWithSession()

    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('1-10 of 12 Records')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(11) // header + 10 body rows
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /^next$/i }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('11-12 of 12 Records')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(3) // header + 2 remaining rows
    expect(screen.getByRole('button', { name: /^next$/i })).toBeDisabled()
  })

  it('jumps directly to a typed page number', async () => {
    const manyStaff: StaffSummary[] = Array.from({ length: 25 }, (_, i) => ({
      roleAssignmentId: `role-assignment-${i}`,
      accountId: `account-${i}`,
      name: `Staff Member ${String(i).padStart(2, '0')}`,
      staffCode: `OP-${1000 + i}`,
      role: 'Operations',
      email: `staff${i}@clinic.example`,
      mobile: null,
      specialization: null,
      experienceYears: null,
      joinedAt: '2024-01-01T00:00:00Z',
      active: true,
    }))
    installFakeStaffBackend(manyStaff)
    renderWithSession()
    await screen.findByText('Page 1 of 3')

    await userEvent.type(screen.getByLabelText(/jump to page/i), '3')
    await userEvent.click(screen.getByRole('button', { name: /^go$/i }))
    expect(await screen.findByText('Page 3 of 3')).toBeInTheDocument()
    expect(screen.getByText('21-25 of 25 Records')).toBeInTheDocument()

    // Out-of-range input clamps to the last page rather than erroring.
    await userEvent.type(screen.getByLabelText(/jump to page/i), '999')
    await userEvent.click(screen.getByRole('button', { name: /^go$/i }))
    expect(await screen.findByText('Page 3 of 3')).toBeInTheDocument()
  })
})
