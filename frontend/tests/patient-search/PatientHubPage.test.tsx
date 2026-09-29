import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PatientHubPage } from '../../src/routes/staff/PatientHubPage'
import { getPatient, listPatientBookings } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return { ...actual, getPatient: vi.fn(), listPatientBookings: vi.fn() }
})

const mockedGetPatient = vi.mocked(getPatient)
const mockedListPatientBookings = vi.mocked(listPatientBookings)

const ACTIVE_COMPLETED_BOOKING = {
  bookingId: 'booking-1',
  sessionDate: '2026-09-01',
  startTime: '09:00:00',
  doctorName: 'Dr. Mehta',
  appointmentTypeName: 'General Consultation',
  bookingStatus: 'ACTIVE' as const,
  slotStatus: 'COMPLETED' as const,
}

const UPCOMING_BOOKING = {
  bookingId: 'booking-2',
  // Far-future on purpose: the hub filters on sessionDate <= today, so a near date silently
  // turns "upcoming" into "started" once the calendar passes it.
  sessionDate: '2099-09-20',
  startTime: '10:00:00',
  doctorName: 'Dr. Mehta',
  appointmentTypeName: 'Follow-up',
  bookingStatus: 'ACTIVE' as const,
  slotStatus: 'BOOKED' as const,
}

function renderWithSession() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/patients/patient-1']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/patients/:patientId" element={<PatientHubPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

// 052-patient-clinical-hub T011: confirms the hub is a navigation surface, not a
// content-aggregation surface (research.md Decision 1) - Consultations/Prescriptions/External
// Records render links into the existing per-booking pages, never note/prescription/record
// content itself.
describe('PatientHubPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedGetPatient.mockReset()
    mockedListPatientBookings.mockReset()
  })

  it('renders the patient identity in the Overview tab', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: '9999900001',
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValueOnce({ bookings: [], page: 0, pageSize: 10, totalCount: 0 })
    renderWithSession()

    expect(await screen.findByRole('heading', { name: 'Asha Rao' })).toBeInTheDocument()
    expect(screen.getByText('9999900001')).toBeInTheDocument()
  })

  it('renders the real booking list on the Bookings tab', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: '9999900001',
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValueOnce({
      bookings: [ACTIVE_COMPLETED_BOOKING],
      page: 0,
      pageSize: 10,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('tab', { name: 'Bookings' }))

    expect(await screen.findByText('Dr. Mehta')).toBeInTheDocument()
    expect(screen.getByText('General Consultation')).toBeInTheDocument()
  })

  it('shows an empty state when the patient has no bookings', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: null,
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValueOnce({ bookings: [], page: 0, pageSize: 10, totalCount: 0 })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('tab', { name: 'Bookings' }))

    expect(await screen.findByText('No bookings at this clinic yet.')).toBeInTheDocument()
  })

  it('lists only bookings whose session has started on the Consultations tab, linking into the existing consultation-note page', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: '9999900001',
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValueOnce({
      bookings: [ACTIVE_COMPLETED_BOOKING, UPCOMING_BOOKING],
      page: 0,
      pageSize: 10,
      totalCount: 2,
    })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('tab', { name: 'Consultations' }))

    const link = await screen.findByRole('link', { name: /Dr\. Mehta/ })
    expect(link).toHaveAttribute('href', '/staff/clinics/clinic-1/bookings/booking-1/consultation-note')
    // The upcoming (not-yet-started) booking must not appear here.
    expect(screen.queryByText('Follow-up')).not.toBeInTheDocument()
  })

  it('links into the correct existing page for the Prescriptions and External Records tabs', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: '9999900001',
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValue({
      bookings: [ACTIVE_COMPLETED_BOOKING],
      page: 0,
      pageSize: 10,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    await user.click(await screen.findByRole('tab', { name: 'Prescriptions' }))
    expect(await screen.findByRole('link', { name: /Dr\. Mehta/ })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/bookings/booking-1/prescription',
    )

    await user.click(await screen.findByRole('tab', { name: 'External Records' }))
    expect(await screen.findByRole('link', { name: /Dr\. Mehta/ })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/bookings/booking-1/external-record',
    )
  })

  it('shows an explicit anonymized indicator, reflecting the real, current API value', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Anonymized Patient',
      phone: null,
      anonymizedAt: '2026-09-10T10:00:00Z',
    })
    mockedListPatientBookings.mockResolvedValueOnce({ bookings: [], page: 0, pageSize: 10, totalCount: 0 })
    renderWithSession()

    expect(await screen.findByText('Anonymized Patient')).toBeInTheDocument()
    expect(screen.getByText('Anonymized')).toBeInTheDocument()
  })

  it('renders no edit or delete control anywhere in the clinical-record tabs - links only', async () => {
    mockedGetPatient.mockResolvedValueOnce({
      patientId: 'patient-1',
      name: 'Asha Rao',
      phone: '9999900001',
      anonymizedAt: null,
    })
    mockedListPatientBookings.mockResolvedValue({
      bookings: [ACTIVE_COMPLETED_BOOKING],
      page: 0,
      pageSize: 10,
      totalCount: 1,
    })
    const user = userEvent.setup()
    renderWithSession()

    for (const tabName of ['Consultations', 'Prescriptions', 'External Records']) {
      await user.click(await screen.findByRole('tab', { name: tabName }))
      await screen.findByRole('link', { name: /Dr\. Mehta/ })
      expect(screen.queryByRole('button', { name: /edit|delete/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    }
  })
})
