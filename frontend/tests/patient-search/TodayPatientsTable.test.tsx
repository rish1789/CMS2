import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TodayPatientsTable } from '../../src/features/patient-search/TodayPatientsTable'
import { getTodayPatients, type TodayPatient } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return { ...actual, getTodayPatients: vi.fn() }
})

const mockedGetTodayPatients = vi.mocked(getTodayPatients)

const scheduledPatient: TodayPatient = {
  bookingId: 'booking-1',
  patientId: 'patient-1',
  patientName: 'Asha Rao',
  patientPhone: '9999900001',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Furaka Singh',
  mode: 'FIXED_TIME',
  startTime: '09:30:00',
  tokenNumber: null,
  slotStatus: 'BOOKED',
  isWalkIn: false,
}

const walkInPatient: TodayPatient = {
  bookingId: 'booking-2',
  patientId: 'patient-2',
  patientName: 'Karan Singh',
  patientPhone: '9999900002',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Furaka Singh',
  mode: 'FIXED_TIME',
  startTime: '18:00:00',
  tokenNumber: null,
  slotStatus: 'COMPLETED',
  isWalkIn: true,
}

function renderTable() {
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'ops@clinic.example' })
  render(
    <MemoryRouter>
      <TodayPatientsTable clinicId="clinic-1" />
    </MemoryRouter>,
  )
}

describe('TodayPatientsTable (real-bug-fix 2026-09-17)', () => {
  beforeEach(() => {
    mockedGetTodayPatients.mockReset()
  })

  it('lists both an appointment-based and a walk-in patient, with distinguishing badges', async () => {
    mockedGetTodayPatients.mockResolvedValueOnce([scheduledPatient, walkInPatient])
    renderTable()

    expect(await screen.findByText('Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Karan Singh')).toBeInTheDocument()
    expect(screen.getByText('Appointment')).toBeInTheDocument()
    expect(screen.getByText('Walk-in')).toBeInTheDocument()
    expect(screen.getByText('Booked')).toBeInTheDocument()
    expect(screen.getByText('Completed')).toBeInTheDocument()
    expect(screen.getByText('09:30')).toBeInTheDocument()
    expect(screen.getByText('18:00')).toBeInTheDocument()
  })

  it('links the patient name into the patient hub', async () => {
    mockedGetTodayPatients.mockResolvedValueOnce([scheduledPatient])
    renderTable()

    const link = await screen.findByRole('link', { name: 'Asha Rao' })
    expect(link).toHaveAttribute('href', '/staff/clinics/clinic-1/patients/patient-1')
  })

  it('shows a Token label for a Queue-mode entry instead of a clock time', async () => {
    mockedGetTodayPatients.mockResolvedValueOnce([
      { ...scheduledPatient, mode: 'QUEUE', startTime: null, tokenNumber: 4 },
    ])
    renderTable()

    expect(await screen.findByText('Token 4')).toBeInTheDocument()
  })

  it('shows a clear empty state when nobody is booked today', async () => {
    mockedGetTodayPatients.mockResolvedValueOnce([])
    renderTable()

    expect(await screen.findByText(/no patients booked today yet/i)).toBeInTheDocument()
  })

  it('shows an inline error if the request fails', async () => {
    mockedGetTodayPatients.mockRejectedValueOnce(new Error('network error'))
    renderTable()

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to load/i)
  })
})
