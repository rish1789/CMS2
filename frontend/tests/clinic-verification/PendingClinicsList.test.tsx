import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PendingClinicsList } from '../../src/features/clinic-verification/PendingClinicsList'
import {
  AdminApiError,
  listClinics,
  verifyClinic,
  unverifyClinic,
  rejectClinic,
  rejectClinicsBulk,
  restoreClinic,
  deleteClinic,
  deleteClinicsBulk,
} from '../../src/features/clinic-verification/api'
import { storeSuperAdminSession } from '../../src/features/super-admin/token'

vi.mock('../../src/features/clinic-verification/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/clinic-verification/api')>(
    '../../src/features/clinic-verification/api',
  )
  return {
    ...actual,
    listClinics: vi.fn(),
    verifyClinic: vi.fn(),
    unverifyClinic: vi.fn(),
    rejectClinic: vi.fn(),
    rejectClinicsBulk: vi.fn(),
    restoreClinic: vi.fn(),
    deleteClinic: vi.fn(),
    deleteClinicsBulk: vi.fn(),
  }
})

const mockedListClinics = vi.mocked(listClinics)
const mockedVerifyClinic = vi.mocked(verifyClinic)
const mockedUnverifyClinic = vi.mocked(unverifyClinic)
const mockedRejectClinic = vi.mocked(rejectClinic)
const mockedRejectClinicsBulk = vi.mocked(rejectClinicsBulk)
const mockedRestoreClinic = vi.mocked(restoreClinic)
const mockedDeleteClinic = vi.mocked(deleteClinic)
const mockedDeleteClinicsBulk = vi.mocked(deleteClinicsBulk)

const pendingClinic = {
  clinicId: 'clinic-1',
  name: 'Sunrise Clinic',
  address: '12 MG Road, Pune',
  contactEmail: null,
  contactMobile: null,
  createdAt: '2026-09-01T10:00:00Z',
  rejected: false,
  rejectionReason: null,
  rejectionDetail: null,
  rejectedAt: null,
  rejectedBy: null,
}

const secondPendingClinic = {
  ...pendingClinic,
  clinicId: 'clinic-3',
  name: 'Willow Clinic',
  address: '9 Willow Ave, Pune',
}

const verifiedClinic = {
  clinicId: 'clinic-2',
  name: 'Riverside Clinic',
  address: '4 Park Street, Pune',
  contactEmail: 'contact@riverside.example',
  contactMobile: null,
  createdAt: '2026-08-15T10:00:00Z',
  rejected: false,
  rejectionReason: null,
  rejectionDetail: null,
  rejectedAt: null,
  rejectedBy: null,
}

const rejectedClinic = {
  clinicId: 'clinic-4',
  name: 'Fraudulent Clinic',
  address: '1 Nowhere Lane',
  contactEmail: null,
  contactMobile: null,
  createdAt: '2026-08-01T10:00:00Z',
  rejected: true,
  rejectionReason: 'SUSPECTED_FRAUD' as const,
  rejectionDetail: 'Address does not exist',
  rejectedAt: '2026-08-02T10:00:00Z',
  rejectedBy: 'super-admin',
}

// 040-super-admin-rbac-login: this screen no longer collects its own credentials - a
// Super Admin session (established at the Clinic Portal, /staff/login) is expected to
// already exist by the time RequireSuperAdminSession lets a visitor reach this route.
function renderWithSession() {
  storeSuperAdminSession({ token: 'super-admin-jwt', username: 'super-admin' })
  render(
    <MemoryRouter initialEntries={['/super-admin-console/clinics']}>
      <Routes>
        <Route path="/staff/login" element={<div>Clinic sign in</div>} />
        <Route path="/super-admin-console/clinics" element={<PendingClinicsList />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PendingClinicsList', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListClinics.mockReset()
    mockedVerifyClinic.mockReset()
    mockedUnverifyClinic.mockReset()
    mockedRejectClinic.mockReset()
    mockedRejectClinicsBulk.mockReset()
    mockedRestoreClinic.mockReset()
    mockedDeleteClinic.mockReset()
    mockedDeleteClinicsBulk.mockReset()
  })

  it('loads and renders the Pending tab using the stored Super Admin session token, passing verified=false', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })

    renderWithSession()

    await waitFor(() => {
      expect(screen.getByText('Sunrise Clinic')).toBeInTheDocument()
    })
    expect(mockedListClinics).toHaveBeenCalledWith('PENDING', 'super-admin-jwt', { page: 0, size: 15, sort: 'createdAt', direction: 'desc' })
  })

  it('switches to the Verified tab and requests verified=true', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('Sunrise Clinic')).toBeInTheDocument())

    mockedListClinics.mockResolvedValueOnce({ clinics: [verifiedClinic], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))

    await waitFor(() => {
      expect(screen.getByText('Riverside Clinic')).toBeInTheDocument()
    })
    expect(mockedListClinics).toHaveBeenLastCalledWith('VERIFIED', 'super-admin-jwt', { page: 0, size: 15, sort: 'createdAt', direction: 'desc' })
  })

  it('verify action calls the API and removes the clinic from the Pending list on success', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    mockedVerifyClinic.mockResolvedValueOnce({ clinicId: 'clinic-1', verified: true })
    const user = userEvent.setup()
    renderWithSession()

    const row = await screen.findByText('Sunrise Clinic')
    // pagination-unification-2026-09-10: a successful action re-fetches this page server-side
    // (the clinic moved to the other tab) rather than filtering it out of a stale in-memory list.
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /verify/i }))

    await waitFor(() => {
      expect(mockedVerifyClinic).toHaveBeenCalledWith('clinic-1', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText('Sunrise Clinic')).not.toBeInTheDocument()
    })
  })

  it('un-verify action requires a confirm step, then calls the API and removes the clinic from the Verified list', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('Sunrise Clinic')).toBeInTheDocument())

    mockedListClinics.mockResolvedValueOnce({ clinics: [verifiedClinic], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))
    const row = await screen.findByText('Riverside Clinic')

    // super-admin-console-redesign-2026-09-11: un-verifying cascades to auto-cancel the
    // clinic's future bookings, so a single click must never be enough - matches the
    // idle/confirming/submitting shape already used by CancelSessionButton elsewhere.
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^un-verify$/i }))
    expect(mockedUnverifyClinic).not.toHaveBeenCalled()

    mockedUnverifyClinic.mockResolvedValueOnce({ clinicId: 'clinic-2', verified: false })
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /confirm/i }))

    await waitFor(() => {
      expect(mockedUnverifyClinic).toHaveBeenCalledWith('clinic-2', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText('Riverside Clinic')).not.toBeInTheDocument()
    })
  })

  it('offers a Back button to cancel out of the un-verify confirm step without calling the API', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('Sunrise Clinic')).toBeInTheDocument())

    mockedListClinics.mockResolvedValueOnce({ clinics: [verifiedClinic], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))
    const row = await screen.findByText('Riverside Clinic')

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^un-verify$/i }))
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /back/i }))

    expect(mockedUnverifyClinic).not.toHaveBeenCalled()
    expect(within(row.closest('tr')!).getByRole('button', { name: /^un-verify$/i })).toBeInTheDocument()
  })

  it('single Remove opens the reject modal, requires a reason, and rejects on submit', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    const row = await screen.findByText('Sunrise Clinic')

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /remove/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: /^reject$/i })).toBeDisabled()

    await user.selectOptions(within(dialog).getByLabelText(/reason/i), 'SUSPECTED_FRAUD')
    expect(within(dialog).getByRole('button', { name: /^reject$/i })).toBeEnabled()

    mockedRejectClinic.mockResolvedValueOnce({ ...pendingClinic, rejected: true })
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(dialog).getByRole('button', { name: /^reject$/i }))

    await waitFor(() => {
      expect(mockedRejectClinic).toHaveBeenCalledWith('clinic-1', 'SUSPECTED_FRAUD', '', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('selecting multiple Pending clinics and rejecting sends every selected id in one bulk call', async () => {
    mockedListClinics.mockResolvedValueOnce({
      clinics: [pendingClinic, secondPendingClinic],
      page: 0,
      pageSize: 15,
      totalCount: 2,
    })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Sunrise Clinic')

    await user.click(screen.getByRole('checkbox', { name: /select sunrise clinic/i }))
    await user.click(screen.getByRole('checkbox', { name: /select willow clinic/i }))
    expect(screen.getByText('2 selected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /reject selected/i }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Sunrise Clinic')).toBeInTheDocument()
    expect(within(dialog).getByText('Willow Clinic')).toBeInTheDocument()

    await user.selectOptions(within(dialog).getByLabelText(/reason/i), 'DUPLICATE_REGISTRATION')
    mockedRejectClinicsBulk.mockResolvedValueOnce({ succeeded: ['clinic-1', 'clinic-3'], failed: {} })
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(dialog).getByRole('button', { name: /reject 2/i }))

    await waitFor(() => {
      expect(mockedRejectClinicsBulk).toHaveBeenCalledWith(
        ['clinic-1', 'clinic-3'],
        'DUPLICATE_REGISTRATION',
        '',
        'super-admin-jwt',
      )
    })
  })

  it('shows the rejection reason/detail on the Rejected tab and restores it back to Pending', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Sunrise Clinic')

    mockedListClinics.mockResolvedValueOnce({ clinics: [rejectedClinic], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    const row = await screen.findByText('Fraudulent Clinic')

    // The reason-filter dropdown also renders "Suspected fraud" as an <option> - scope to the
    // row to avoid an ambiguous multi-match.
    expect(within(row.closest('tr')!).getByText('Suspected fraud')).toBeInTheDocument()
    expect(within(row.closest('tr')!).getByText('Address does not exist')).toBeInTheDocument()

    mockedRestoreClinic.mockResolvedValueOnce({ ...rejectedClinic, rejected: false })
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /restore/i }))

    await waitFor(() => {
      expect(mockedRestoreClinic).toHaveBeenCalledWith('clinic-4', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText('Fraudulent Clinic')).not.toBeInTheDocument()
    })
  })

  it('single Delete on the Rejected tab requires typing the exact name before it enables, then permanently deletes', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Sunrise Clinic')

    mockedListClinics.mockResolvedValueOnce({ clinics: [rejectedClinic], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    const row = await screen.findByText('Fraudulent Clinic')

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^delete$/i }))
    const dialog = screen.getByRole('dialog')
    const deleteButton = within(dialog).getByRole('button', { name: /delete permanently/i })
    expect(deleteButton).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/type/i), 'Fraudulent Clinic')
    expect(deleteButton).toBeEnabled()

    mockedDeleteClinic.mockResolvedValueOnce(undefined)
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(mockedDeleteClinic).toHaveBeenCalledWith('clinic-4', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('selecting multiple Rejected clinics and deleting requires typing DELETE, then sends every selected id in one bulk call', async () => {
    mockedListClinics.mockResolvedValueOnce({ clinics: [pendingClinic], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Sunrise Clinic')

    const secondRejectedClinic = { ...rejectedClinic, clinicId: 'clinic-5', name: 'Shady Clinic' }
    mockedListClinics.mockResolvedValueOnce({
      clinics: [rejectedClinic, secondRejectedClinic],
      page: 0,
      pageSize: 15,
      totalCount: 2,
    })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    await screen.findByText('Fraudulent Clinic')

    await user.click(screen.getByRole('checkbox', { name: /select fraudulent clinic/i }))
    await user.click(screen.getByRole('checkbox', { name: /select shady clinic/i }))
    expect(screen.getByText('2 selected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /delete selected permanently/i }))
    const dialog = screen.getByRole('dialog')
    const deleteButton = within(dialog).getByRole('button', { name: /delete 2 permanently/i })
    expect(deleteButton).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/type/i), 'DELETE')
    expect(deleteButton).toBeEnabled()

    mockedDeleteClinicsBulk.mockResolvedValueOnce({ succeeded: ['clinic-4', 'clinic-5'], failed: {} })
    mockedListClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(mockedDeleteClinicsBulk).toHaveBeenCalledWith(['clinic-4', 'clinic-5'], 'super-admin-jwt')
    })
  })

  it('040-super-admin-rbac-login (US3): redirects to the Clinic Portal login and clears the session on a 401 response', async () => {
    mockedListClinics.mockRejectedValueOnce(new AdminApiError(401, 'Your Super Admin session has expired.'))

    renderWithSession()

    await waitFor(() => {
      expect(screen.getByText('Clinic sign in')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('cms.superAdminToken')).toBeNull()
  })
})
