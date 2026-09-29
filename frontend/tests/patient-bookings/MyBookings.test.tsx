import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MyBookings } from '../../src/features/patient-bookings/MyBookings'
import { listMyBookings, type PatientBookingSummary } from '../../src/features/patient-bookings/api'
import { getClinicalRecordAvailability } from '../../src/features/patient-clinical-records/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-bookings/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-bookings/api')>(
    '../../src/features/patient-bookings/api',
  )
  return { ...actual, listMyBookings: vi.fn() }
})

vi.mock('../../src/features/patient-clinical-records/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-clinical-records/api')>(
    '../../src/features/patient-clinical-records/api',
  )
  return { ...actual, getClinicalRecordAvailability: vi.fn() }
})

const mockedListMyBookings = vi.mocked(listMyBookings)
const mockedGetClinicalRecordAvailability = vi.mocked(getClinicalRecordAvailability)

const DOCUMENTED_BOOKING: PatientBookingSummary = {
  id: 'booking-documented',
  clinicId: 'clinic-1',
  clinicName: 'Sunrise Clinic',
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Nair',
  appointmentTypeName: 'General Consultation',
  mode: 'FIXED_TIME',
  sessionDate: '2026-08-01',
  startTime: '10:00:00',
  tokenNumber: null,
  status: 'ACTIVE',
  paymentStatus: 'PAID',
  lockedFee: 500,
  createdAt: '2026-08-01T09:00:00Z',
  cancellationReason: null,
}

const UNDOCUMENTED_BOOKING: PatientBookingSummary = { ...DOCUMENTED_BOOKING, id: 'booking-undocumented' }

function renderMyBookings() {
  render(
    <MemoryRouter>
      <MyBookings />
    </MemoryRouter>,
  )
}

describe('MyBookings - clinical record availability indicator (059-patient-clinical-record-access US1)', () => {
  beforeEach(() => {
    mockedListMyBookings.mockReset()
    mockedGetClinicalRecordAvailability.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it('shows a record-available indicator only for bookings the availability check reports', async () => {
    mockedListMyBookings.mockResolvedValueOnce({
      bookings: [DOCUMENTED_BOOKING, UNDOCUMENTED_BOOKING],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })
    mockedGetClinicalRecordAvailability.mockResolvedValueOnce(new Set([DOCUMENTED_BOOKING.id]))

    renderMyBookings()

    const rows = await screen.findAllByRole('link', { name: /dr\. nair/i })
    const documentedRow = rows.find((row) => row.getAttribute('href') === `/patient/bookings/${DOCUMENTED_BOOKING.id}`)
    const undocumentedRow = rows.find((row) => row.getAttribute('href') === `/patient/bookings/${UNDOCUMENTED_BOOKING.id}`)
    expect(documentedRow).toHaveTextContent(/record available/i)
    expect(undocumentedRow).not.toHaveTextContent(/record available/i)
  })
})

// 062-rejected-clinic-gating FR-010: a booking the system cancelled because the Super Admin rejected
// the clinic explains why, instead of the bare "Cancelled" badge a patient-initiated cancel shows.
describe('MyBookings - clinic-rejected cancellation message (062-rejected-clinic-gating)', () => {
  beforeEach(() => {
    mockedListMyBookings.mockReset()
    mockedGetClinicalRecordAvailability.mockReset()
    mockedGetClinicalRecordAvailability.mockResolvedValue(new Set())
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it('explains a CLINIC_REJECTED cancellation and adds nothing to other cancellations', async () => {
    mockedListMyBookings.mockResolvedValueOnce({
      bookings: [
        { ...DOCUMENTED_BOOKING, id: 'booking-rejected', status: 'CANCELLED', cancellationReason: 'CLINIC_REJECTED' },
        { ...DOCUMENTED_BOOKING, id: 'booking-self-cancelled', status: 'CANCELLED', cancellationReason: 'FEELING_BETTER' },
      ],
      page: 0,
      pageSize: 20,
      totalCount: 2,
    })

    renderMyBookings()

    expect(await screen.findByText('This clinic is no longer accepting appointments.')).toBeInTheDocument()
    // Both rows keep the plain status badge; only the rejected one carries the explanation.
    expect(screen.getAllByText('Cancelled')).toHaveLength(2)
    expect(screen.getAllByText('This clinic is no longer accepting appointments.')).toHaveLength(1)
  })
})
