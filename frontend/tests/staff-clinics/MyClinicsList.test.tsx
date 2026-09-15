import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MyClinicsList } from '../../src/features/staff-clinics/MyClinicsList'
import { listMyClinics } from '../../src/features/staff-clinics/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/staff-clinics/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-clinics/api')>(
    '../../src/features/staff-clinics/api',
  )
  return { ...actual, listMyClinics: vi.fn() }
})

const mockedListMyClinics = vi.mocked(listMyClinics)

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff']}>
      <Routes>
        <Route path="/staff" element={<MyClinicsList />} />
        <Route path="/staff/clinics/:clinicId" element={<div>Clinic workspace</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MyClinicsList (041-staff-console-pickers T003/US1)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListMyClinics.mockReset()
  })

  it('renders clinic names from the API and navigates on click', async () => {
    mockedListMyClinics.mockResolvedValueOnce({
      clinics: [{ clinicId: 'clinic-1', name: 'Sunrise Clinic', address: '12 MG Road', role: 'Operations' }],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    expect(await screen.findByText('Sunrise Clinic')).toBeInTheDocument()
    await user.click(screen.getByText('Sunrise Clinic'))

    await waitFor(() => {
      expect(screen.getByText('Clinic workspace')).toBeInTheDocument()
    })
  })

  it('shows a clear empty state when the caller has no clinics', async () => {
    mockedListMyClinics.mockResolvedValueOnce({ clinics: [], page: 0, pageSize: 20, totalCount: 0 })
    renderWithSession()

    expect(await screen.findByText(/don't have an active role/i)).toBeInTheDocument()
  })
})
