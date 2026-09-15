import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueuePositionIndicator } from '../../src/features/queue-position/QueuePositionIndicator'
import { getQueuePositionAsPatient, getQueuePositionAsStaff } from '../../src/features/queue-position/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/queue-position/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/queue-position/api')>(
    '../../src/features/queue-position/api',
  )
  return {
    ...actual,
    getQueuePositionAsStaff: vi.fn(),
    getQueuePositionAsPatient: vi.fn(),
  }
})

const mockedGetAsStaff = vi.mocked(getQueuePositionAsStaff)
const mockedGetAsPatient = vi.mocked(getQueuePositionAsPatient)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

describe('QueuePositionIndicator', () => {
  beforeEach(() => {
    mockedGetAsStaff.mockReset()
    mockedGetAsPatient.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
    storePatientSession({ token: 'a.patient.token', patientAccountId: 'pt-1', email: 'patient@example.com' })
  })

  it('renders the position for staff mode', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({ bookingId: BOOKING_ID, applicable: true, position: 3 })

    render(<QueuePositionIndicator mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/queue position: 3/i)).toBeInTheDocument()
    expect(mockedGetAsStaff).toHaveBeenCalledWith(CLINIC_ID, BOOKING_ID, 'a.jwt.token')
  })

  it('renders the position for patient mode', async () => {
    mockedGetAsPatient.mockResolvedValueOnce({ bookingId: BOOKING_ID, applicable: true, position: 1 })

    render(<QueuePositionIndicator mode="patient" bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/queue position: 1/i)).toBeInTheDocument()
    expect(mockedGetAsPatient).toHaveBeenCalledWith(BOOKING_ID, 'a.patient.token')
  })

  it('shows an explicit message when not applicable, rather than rendering nothing', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({ bookingId: BOOKING_ID, applicable: false, position: null })

    render(<QueuePositionIndicator mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/isn't in a queue/i)).toBeInTheDocument()
    expect(screen.queryByText(/queue position:/i)).not.toBeInTheDocument()
  })

  // staff-console-audit-2026-09-10 P2: "renders nothing" used to also cover the loading and
  // error cases - a staff member had no way to tell "broken" from "not applicable."
  it('shows a loading state before the first fetch resolves', () => {
    mockedGetAsStaff.mockReturnValueOnce(new Promise(() => {})) // never resolves during this test

    render(<QueuePositionIndicator mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(screen.getByRole('status')).toHaveTextContent(/checking queue position/i)
  })

  it('shows an error message when the fetch fails, distinct from not-applicable', async () => {
    mockedGetAsStaff.mockRejectedValueOnce(new Error('network error'))

    render(<QueuePositionIndicator mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't load the queue position/i)
  })
})
