import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PatientSearch } from '../../src/features/patient-search/PatientSearch'
import { searchPatients } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return { ...actual, searchPatients: vi.fn() }
})

const mockedSearchPatients = vi.mocked(searchPatients)

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/patients/search']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/patients/search" element={<PatientSearch />} />
        <Route path="/staff/clinics/:clinicId/patients/:patientId/anonymize" element={<div>Anonymize page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PatientSearch (041-staff-console-pickers T026/US3)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedSearchPatients.mockReset()
  })

  it('shows matching patients by name, with the patient info itself not clickable', async () => {
    mockedSearchPatients.mockResolvedValueOnce({
      patients: [{ patientId: 'patient-1', name: 'Asha Rao', phone: '9999900001' }],
      page: 0,
      pageSize: 15,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    await user.type(screen.getByLabelText(/search by name or phone/i), 'Asha')
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(await screen.findByText('Asha Rao')).toBeInTheDocument()
    // staff-console-audit-2026-09-10 P1: clicking the patient's own name/info must NOT navigate
    // anywhere - only the explicit "Anonymize" action does. Confirmed by role, not by clicking
    // the name itself (there is nothing there to click).
    expect(screen.queryByRole('link', { name: /asha rao/i })).not.toBeInTheDocument()
  })

  it('navigates to the anonymize route only via the explicit Anonymize action', async () => {
    mockedSearchPatients.mockResolvedValueOnce({
      patients: [{ patientId: 'patient-1', name: 'Asha Rao', phone: '9999900001' }],
      page: 0,
      pageSize: 15,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    await user.type(screen.getByLabelText(/search by name or phone/i), 'Asha')
    await user.click(screen.getByRole('button', { name: /search/i }))

    await user.click(await screen.findByRole('link', { name: /^anonymize$/i }))

    await waitFor(() => {
      expect(screen.getByText('Anonymize page')).toBeInTheDocument()
    })
  })

  it('shows a clear no-matches state', async () => {
    mockedSearchPatients.mockResolvedValueOnce({ patients: [], page: 0, pageSize: 15, totalCount: 0 })
    const user = userEvent.setup()
    renderWithSession()

    await user.type(screen.getByLabelText(/search by name or phone/i), 'zzznomatch')
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(await screen.findByText(/no matching patients/i)).toBeInTheDocument()
  })
})
