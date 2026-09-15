import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PendingDoctorsList } from '../../src/features/doctor-verification/PendingDoctorsList'
import {
  AdminApiError,
  listDoctors,
  verifyDoctor,
  revokeDoctor,
  editDoctor,
  rejectDoctor,
  rejectDoctorsBulk,
  restoreDoctor,
  deleteDoctor,
  deleteDoctorsBulk,
} from '../../src/features/doctor-verification/api'
import { storeSuperAdminSession } from '../../src/features/super-admin/token'

vi.mock('../../src/features/doctor-verification/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-verification/api')>(
    '../../src/features/doctor-verification/api',
  )
  return {
    ...actual,
    listDoctors: vi.fn(),
    verifyDoctor: vi.fn(),
    revokeDoctor: vi.fn(),
    editDoctor: vi.fn(),
    rejectDoctor: vi.fn(),
    rejectDoctorsBulk: vi.fn(),
    restoreDoctor: vi.fn(),
    deleteDoctor: vi.fn(),
    deleteDoctorsBulk: vi.fn(),
  }
})

const mockedListDoctors = vi.mocked(listDoctors)
const mockedVerifyDoctor = vi.mocked(verifyDoctor)
const mockedRevokeDoctor = vi.mocked(revokeDoctor)
const mockedEditDoctor = vi.mocked(editDoctor)
const mockedRejectDoctor = vi.mocked(rejectDoctor)
const mockedRejectDoctorsBulk = vi.mocked(rejectDoctorsBulk)
const mockedRestoreDoctor = vi.mocked(restoreDoctor)
const mockedDeleteDoctor = vi.mocked(deleteDoctor)
const mockedDeleteDoctorsBulk = vi.mocked(deleteDoctorsBulk)

const pendingDoctor = {
  doctorProfileId: 'doctor-1',
  accountId: 'account-1',
  accountName: 'Dr. Anita Rao',
  accountEmail: 'anita.rao@clinic.example',
  specialization: 'ENT',
  licenseNumber: 'LIC-001',
  experienceYears: 5,
  licenseVerified: false,
  visible: true,
  rejected: false,
  rejectionReason: null,
  rejectionDetail: null,
  rejectedAt: null,
  rejectedBy: null,
}

const secondPendingDoctor = {
  ...pendingDoctor,
  doctorProfileId: 'doctor-3',
  accountId: 'account-3',
  accountName: 'Dr. Bilal Khan',
  accountEmail: 'bilal.khan@clinic.example',
  licenseNumber: 'LIC-003',
}

const verifiedDoctor = {
  doctorProfileId: 'doctor-2',
  accountId: 'account-2',
  accountName: 'Dr. Vikram Shah',
  accountEmail: 'vikram.shah@clinic.example',
  specialization: 'Radiology',
  licenseNumber: 'LIC-002',
  experienceYears: 8,
  licenseVerified: true,
  visible: true,
  rejected: false,
  rejectionReason: null,
  rejectionDetail: null,
  rejectedAt: null,
  rejectedBy: null,
}

const rejectedDoctor = {
  doctorProfileId: 'doctor-4',
  accountId: 'account-4',
  accountName: 'Dr. Fake Name',
  accountEmail: 'fake@example.com',
  specialization: 'General Medicine',
  licenseNumber: 'FAKE-001',
  experienceYears: 99,
  licenseVerified: false,
  visible: true,
  rejected: true,
  rejectionReason: 'INVALID_DETAILS' as const,
  rejectionDetail: 'License number does not resolve to a real registry entry',
  rejectedAt: '2026-08-02T10:00:00Z',
  rejectedBy: 'super-admin',
}

// 040-super-admin-rbac-login: this screen no longer collects its own credentials - a
// Super Admin session (established at the Clinic Portal, /staff/login) is expected to
// already exist by the time RequireSuperAdminSession lets a visitor reach this route.
function renderWithSession() {
  storeSuperAdminSession({ token: 'super-admin-jwt', username: 'super-admin' })
  render(
    <MemoryRouter initialEntries={['/super-admin-console/doctors']}>
      <Routes>
        <Route path="/staff/login" element={<div>Clinic sign in</div>} />
        <Route path="/super-admin-console/doctors" element={<PendingDoctorsList />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PendingDoctorsList', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListDoctors.mockReset()
    mockedVerifyDoctor.mockReset()
    mockedRevokeDoctor.mockReset()
    mockedEditDoctor.mockReset()
    mockedRejectDoctor.mockReset()
    mockedRejectDoctorsBulk.mockReset()
    mockedRestoreDoctor.mockReset()
    mockedDeleteDoctor.mockReset()
    mockedDeleteDoctorsBulk.mockReset()
  })

  it('loads and renders the Pending tab using the stored Super Admin session token, passing verified=false', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })

    renderWithSession()

    await waitFor(() => {
      expect(screen.getByText('ENT')).toBeInTheDocument()
    })
    expect(screen.getByText('Dr. Anita Rao')).toBeInTheDocument()
    expect(screen.getByText(/LIC-001/)).toBeInTheDocument()
    expect(mockedListDoctors).toHaveBeenCalledWith('PENDING', 'super-admin-jwt', { page: 0, size: 15, sort: 'createdAt', direction: 'desc' })
  })

  it('switches to the Verified tab and requests verified=true', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('ENT')).toBeInTheDocument())

    mockedListDoctors.mockResolvedValueOnce({ doctors: [verifiedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))

    await waitFor(() => {
      expect(screen.getByText('Radiology')).toBeInTheDocument()
    })
    expect(mockedListDoctors).toHaveBeenLastCalledWith('VERIFIED', 'super-admin-jwt', { page: 0, size: 15, sort: 'createdAt', direction: 'desc' })
  })

  it('verify action calls the API and removes the doctor from the Pending list on success', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    mockedVerifyDoctor.mockResolvedValueOnce({ doctorProfileId: 'doctor-1', licenseVerified: true })
    const user = userEvent.setup()
    renderWithSession()

    const row = await screen.findByText(/LIC-001/)
    // pagination-unification-2026-09-10: a successful action re-fetches this page server-side
    // (the doctor moved to the other tab) rather than filtering it out of a stale in-memory list.
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /verify/i }))

    await waitFor(() => {
      expect(mockedVerifyDoctor).toHaveBeenCalledWith('doctor-1', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText(/LIC-001/)).not.toBeInTheDocument()
    })
  })

  it('revoke action requires a confirm step, then calls the API and removes the doctor from the Verified list', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('ENT')).toBeInTheDocument())

    mockedListDoctors.mockResolvedValueOnce({ doctors: [verifiedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))
    const row = await screen.findByText(/LIC-002/)

    // super-admin-console-redesign-2026-09-11: revoking un-verifies a license outright, so a
    // single click must never be enough - same idle/confirming/submitting shape as
    // CancelSessionButton elsewhere.
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^revoke$/i }))
    expect(mockedRevokeDoctor).not.toHaveBeenCalled()

    mockedRevokeDoctor.mockResolvedValueOnce({ doctorProfileId: 'doctor-2', licenseVerified: false })
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /confirm/i }))

    await waitFor(() => {
      expect(mockedRevokeDoctor).toHaveBeenCalledWith('doctor-2', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText(/LIC-002/)).not.toBeInTheDocument()
    })
  })

  it('offers a Back button to cancel out of the revoke confirm step without calling the API', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('ENT')).toBeInTheDocument())

    mockedListDoctors.mockResolvedValueOnce({ doctors: [verifiedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))
    const row = await screen.findByText(/LIC-002/)

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^revoke$/i }))
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /back/i }))

    expect(mockedRevokeDoctor).not.toHaveBeenCalled()
    expect(within(row.closest('tr')!).getByRole('button', { name: /^revoke$/i })).toBeInTheDocument()
  })

  it('edit action submits a license-number change and the row leaves the Verified tab once no longer verified', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await waitFor(() => expect(screen.getByText('ENT')).toBeInTheDocument())

    mockedListDoctors.mockResolvedValueOnce({ doctors: [verifiedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /verified/i }))
    const row = await screen.findByText(/LIC-002/)

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /edit/i }))
    const licenseInput = screen.getByLabelText(/license number/i)
    await user.clear(licenseInput)
    await user.type(licenseInput, 'LIC-002-NEW')
    mockedEditDoctor.mockResolvedValueOnce({
      doctorProfileId: 'doctor-2',
      accountId: 'account-2',
      accountName: 'Dr. Vikram Shah',
      accountEmail: 'vikram.shah@clinic.example',
      specialization: 'Radiology',
      licenseNumber: 'LIC-002-NEW',
      experienceYears: 8,
      licenseVerified: false,
      visible: true,
    })
    // pagination-unification-2026-09-10: the reset drops it off the Verified tab server-side -
    // this triggers a re-fetch of the current page rather than a local filter.
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => {
      expect(mockedEditDoctor).toHaveBeenCalledWith(
        'doctor-2',
        { specialization: 'Radiology', licenseNumber: 'LIC-002-NEW', experienceYears: 8, visible: true },
        'super-admin-jwt',
      )
    })
    await waitFor(() => {
      expect(screen.queryByText('LIC-002-NEW')).not.toBeInTheDocument()
    })
  })

  it('single Remove opens the reject modal, requires a reason, and rejects on submit', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    const row = await screen.findByText('Dr. Anita Rao')

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /remove/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: /^reject$/i })).toBeDisabled()

    await user.selectOptions(within(dialog).getByLabelText(/reason/i), 'INVALID_DETAILS')
    expect(within(dialog).getByRole('button', { name: /^reject$/i })).toBeEnabled()

    mockedRejectDoctor.mockResolvedValueOnce({ ...pendingDoctor, rejected: true })
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(dialog).getByRole('button', { name: /^reject$/i }))

    await waitFor(() => {
      expect(mockedRejectDoctor).toHaveBeenCalledWith('doctor-1', 'INVALID_DETAILS', '', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('selecting multiple Pending doctors and rejecting sends every selected id in one bulk call', async () => {
    mockedListDoctors.mockResolvedValueOnce({
      doctors: [pendingDoctor, secondPendingDoctor],
      page: 0,
      pageSize: 15,
      totalCount: 2,
    })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Dr. Anita Rao')

    await user.click(screen.getByRole('checkbox', { name: /select dr\. anita rao/i }))
    await user.click(screen.getByRole('checkbox', { name: /select dr\. bilal khan/i }))
    expect(screen.getByText('2 selected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /reject selected/i }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Dr. Anita Rao')).toBeInTheDocument()
    expect(within(dialog).getByText('Dr. Bilal Khan')).toBeInTheDocument()

    await user.selectOptions(within(dialog).getByLabelText(/reason/i), 'SUSPECTED_FRAUD')
    mockedRejectDoctorsBulk.mockResolvedValueOnce({ succeeded: ['doctor-1', 'doctor-3'], failed: {} })
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(dialog).getByRole('button', { name: /reject 2/i }))

    await waitFor(() => {
      expect(mockedRejectDoctorsBulk).toHaveBeenCalledWith(
        ['doctor-1', 'doctor-3'],
        'SUSPECTED_FRAUD',
        '',
        'super-admin-jwt',
      )
    })
  })

  it('shows the rejection reason/detail on the Rejected tab and restores it back to Pending', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Dr. Anita Rao')

    mockedListDoctors.mockResolvedValueOnce({ doctors: [rejectedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    const row = await screen.findByText('Dr. Fake Name')

    // The reason-filter dropdown also renders "Invalid details" as an <option> - scope to the
    // row to avoid an ambiguous multi-match.
    expect(within(row.closest('tr')!).getByText('Invalid details')).toBeInTheDocument()
    expect(
      within(row.closest('tr')!).getByText('License number does not resolve to a real registry entry'),
    ).toBeInTheDocument()

    mockedRestoreDoctor.mockResolvedValueOnce({ ...rejectedDoctor, rejected: false })
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(within(row.closest('tr')!).getByRole('button', { name: /restore/i }))

    await waitFor(() => {
      expect(mockedRestoreDoctor).toHaveBeenCalledWith('doctor-4', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByText('Dr. Fake Name')).not.toBeInTheDocument()
    })
  })

  it('single Delete on the Rejected tab requires typing the exact name before it enables, then permanently deletes', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Dr. Anita Rao')

    mockedListDoctors.mockResolvedValueOnce({ doctors: [rejectedDoctor], page: 0, pageSize: 15, totalCount: 1 })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    const row = await screen.findByText('Dr. Fake Name')

    await user.click(within(row.closest('tr')!).getByRole('button', { name: /^delete$/i }))
    const dialog = screen.getByRole('dialog')
    const deleteButton = within(dialog).getByRole('button', { name: /delete permanently/i })
    expect(deleteButton).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/type/i), 'Dr. Fake Name')
    expect(deleteButton).toBeEnabled()

    mockedDeleteDoctor.mockResolvedValueOnce(undefined)
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(mockedDeleteDoctor).toHaveBeenCalledWith('doctor-4', 'super-admin-jwt')
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('selecting multiple Rejected doctors and deleting requires typing DELETE, then sends every selected id in one bulk call', async () => {
    mockedListDoctors.mockResolvedValueOnce({ doctors: [pendingDoctor], page: 0, pageSize: 15, totalCount: 1 })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Dr. Anita Rao')

    const secondRejectedDoctor = {
      ...rejectedDoctor,
      doctorProfileId: 'doctor-5',
      accountName: 'Dr. Shady Name',
    }
    mockedListDoctors.mockResolvedValueOnce({
      doctors: [rejectedDoctor, secondRejectedDoctor],
      page: 0,
      pageSize: 15,
      totalCount: 2,
    })
    await user.click(screen.getByRole('tab', { name: /rejected/i }))
    await screen.findByText('Dr. Fake Name')

    await user.click(screen.getByRole('checkbox', { name: /select dr\. fake name/i }))
    await user.click(screen.getByRole('checkbox', { name: /select dr\. shady name/i }))
    expect(screen.getByText('2 selected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /delete selected permanently/i }))
    const dialog = screen.getByRole('dialog')
    const deleteButton = within(dialog).getByRole('button', { name: /delete 2 permanently/i })
    expect(deleteButton).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/type/i), 'DELETE')
    expect(deleteButton).toBeEnabled()

    mockedDeleteDoctorsBulk.mockResolvedValueOnce({ succeeded: ['doctor-4', 'doctor-5'], failed: {} })
    mockedListDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(mockedDeleteDoctorsBulk).toHaveBeenCalledWith(['doctor-4', 'doctor-5'], 'super-admin-jwt')
    })
  })

  it('040-super-admin-rbac-login (US3): redirects to the Clinic Portal login and clears the session on a 401 response', async () => {
    mockedListDoctors.mockRejectedValueOnce(new AdminApiError(401, 'Your Super Admin session has expired.'))

    renderWithSession()

    await waitFor(() => {
      expect(screen.getByText('Clinic sign in')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('cms.superAdminToken')).toBeNull()
  })
})
