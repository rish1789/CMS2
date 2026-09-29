import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DoctorPicker } from '../../src/features/doctor-picker/DoctorPicker'
import { listClinicDoctors, listDoctorBookingReadiness } from '../../src/features/doctor-picker/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/doctor-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-picker/api')>(
    '../../src/features/doctor-picker/api',
  )
  return { ...actual, listClinicDoctors: vi.fn(), listDoctorBookingReadiness: vi.fn() }
})

const mockedListClinicDoctors = vi.mocked(listClinicDoctors)
const mockedListDoctorBookingReadiness = vi.mocked(listDoctorBookingReadiness)

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
    mockedListDoctorBookingReadiness.mockReset()
    mockedListDoctorBookingReadiness.mockResolvedValue([])
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

  // real-bug-fix 2026-09-17: the actual live-found bug this feature addresses - a newly-
  // onboarded doctor with zero appointment types and no default fee looked identical to a
  // fully-configured one, until a patient hit an empty dropdown on the booking page.
  it('flags a doctor whose appointment types/default fee are not yet configured', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001', specialization: 'Cardiology' },
        { doctorProfileId: 'doctor-2', name: 'Dr. New Hire', staffCode: 'DR-2002', specialization: 'Gastroenterology' },
      ],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    mockedListDoctorBookingReadiness.mockResolvedValueOnce([
      {
        doctorProfileId: 'doctor-1',
        hasAppointmentTypes: true,
        hasDefaultFee: true,
        hasAppointmentTypeMissingFeeOverride: false,
        bookingReady: true,
      },
      {
        doctorProfileId: 'doctor-2',
        hasAppointmentTypes: false,
        hasDefaultFee: false,
        hasAppointmentTypeMissingFeeOverride: false,
        bookingReady: false,
      },
    ])
    renderWithSession()

    await screen.findByText('Dr. New Hire')
    const newHireRow = screen.getByText('Dr. New Hire').closest('tr')
    const readyRow = screen.getByText('Dr. Priya Nair').closest('tr')

    expect(within(newHireRow as HTMLElement).getByText(/booking setup incomplete/i)).toBeInTheDocument()
    expect(within(readyRow as HTMLElement).queryByText(/booking setup incomplete/i)).not.toBeInTheDocument()
  })

  // real-bug-fix 2026-09-17: the actual live-found false positive on the backend side - a
  // doctor whose every appointment type already carries its own fee override is fully bookable
  // despite having no default fee, so the frontend must trust the backend's own `bookingReady`
  // rather than deriving it locally from hasAppointmentTypes/hasDefaultFee alone.
  it('does not flag a doctor whose appointment types all resolve their own fee, despite no default fee', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Gauresh Kumar', staffCode: 'DR-6789', specialization: 'General Medicine' },
      ],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    mockedListDoctorBookingReadiness.mockResolvedValueOnce([
      {
        doctorProfileId: 'doctor-1',
        hasAppointmentTypes: true,
        hasDefaultFee: false,
        hasAppointmentTypeMissingFeeOverride: false,
        bookingReady: true,
      },
    ])
    renderWithSession()

    await screen.findByText('Dr. Gauresh Kumar')
    expect(screen.queryByText(/booking setup incomplete/i)).not.toBeInTheDocument()
  })

  it('does not show the warning for anyone when the readiness check itself fails', async () => {
    mockedListClinicDoctors.mockResolvedValueOnce({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001', specialization: 'Cardiology' },
      ],
      page: 0,
      pageSize: 20,
      totalCount: 1,
    })
    mockedListDoctorBookingReadiness.mockRejectedValueOnce(new Error('network error'))
    renderWithSession()

    await screen.findByText('Dr. Priya Nair')
    expect(screen.queryByText(/booking setup incomplete/i)).not.toBeInTheDocument()
  })
})
