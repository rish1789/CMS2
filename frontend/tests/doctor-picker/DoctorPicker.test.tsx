import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DoctorPicker } from '../../src/features/doctor-picker/DoctorPicker'
import { listClinicDoctors } from '../../src/features/doctor-picker/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/doctor-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-picker/api')>(
    '../../src/features/doctor-picker/api',
  )
  return { ...actual, listClinicDoctors: vi.fn() }
})

const mockedListClinicDoctors = vi.mocked(listClinicDoctors)

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/doctors']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/doctors" element={<DoctorPicker />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DoctorPicker (041-staff-console-pickers T034/US4)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListClinicDoctors.mockReset()
  })

  it('renders doctors by name/specialization with links to both schedule and appointment-types routes', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001', specialization: 'Cardiology' },
      ],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    renderWithSession()

    expect(await screen.findByText('Dr. Priya Nair')).toBeInTheDocument()
    expect(screen.getByText('Cardiology')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /define schedule/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/doctors/doctor-1/schedule',
    )
    expect(screen.getByRole('link', { name: /appointment types/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/doctors/doctor-1/appointment-types',
    )
  })

  it('shows a clear empty state when no doctors are staffed', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 20, totalCount: 0 })
    renderWithSession()

    expect(await screen.findByText(/no doctors staffed/i)).toBeInTheDocument()
  })

  /** doctors-search-2026-09-10: search is debounced, then re-fetches server-side with q set. */
  it('searches server-side after typing, distinctly from the no-doctors-at-all empty state', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({
      doctors: [{ doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001', specialization: 'Cardiology' }],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()
    await screen.findByText('Dr. Priya Nair')

    mockedListClinicDoctors.mockResolvedValueOnce({ doctors: [], page: 0, pageSize: 20, totalCount: 0 })
    await user.type(screen.getByLabelText(/search doctors/i), 'zzznomatch')

    await waitFor(() => expect(screen.getByText(/no doctors match your search/i)).toBeInTheDocument())
    expect(mockedListClinicDoctors).toHaveBeenLastCalledWith('clinic-1', 'staff-jwt', {
      q: 'zzznomatch',
      page: 0,
      size: 20,
    })
  })
})
