import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LiveScheduleStatusIndicator } from '../../src/features/session-delay/LiveScheduleStatusIndicator'
import { getSessionLiveStatusAsPatient, getSessionLiveStatusAsStaff } from '../../src/features/session-delay/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return {
    ...actual,
    getSessionLiveStatusAsStaff: vi.fn(),
    getSessionLiveStatusAsPatient: vi.fn(),
  }
})

const mockedGetAsStaff = vi.mocked(getSessionLiveStatusAsStaff)
const mockedGetAsPatient = vi.mocked(getSessionLiveStatusAsPatient)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'
const BOOKING_ID = 'booking-1'

describe('LiveScheduleStatusIndicator (staff mode)', () => {
  beforeEach(() => {
    mockedGetAsStaff.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('shows a loading state before the first fetch resolves', () => {
    mockedGetAsStaff.mockReturnValueOnce(new Promise(() => {})) // never resolves during this test

    render(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(screen.getByRole('status')).toHaveTextContent(/checking schedule status/i)
  })

  it('shows an error message when the fetch fails, distinct from not-applicable', async () => {
    mockedGetAsStaff.mockRejectedValueOnce(new Error('network error'))

    render(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't load the schedule status/i)
  })

  // BR-016 (2026-09-24): not-applicable covers both Queue-mode and a past-operational-day session,
  // and the clarified decision is that the indicator disappears rather than explaining itself.
  it('renders nothing once the response is not applicable', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({
      sessionId: SESSION_ID,
      applicable: false,
      status: null,
      currentPatientOrdinal: null,
      expectedPatientOrdinal: null,
      deviationMinutes: null,
      firstSlotTime: null,
      operationalDay: null,
    })

    const { container } = render(
      <LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />,
    )

    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  it('renders Current Patient, Expected Patient, Schedule Status, Minutes Early/Late, First Slot, and Operational Day', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({
      sessionId: SESSION_ID,
      applicable: true,
      status: 'DELAYED',
      currentPatientOrdinal: 1,
      expectedPatientOrdinal: 3,
      deviationMinutes: 15,
      firstSlotTime: '09:00:00',
      operationalDay: '2026-09-23',
    })

    render(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByText('Delayed')).toBeInTheDocument()
    expect(screen.getByText(/15 min late/)).toBeInTheDocument()
    expect(screen.getByText('Current Patient')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('Expected Patient')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('First Slot')).toBeInTheDocument()
    expect(screen.getByText('09:00')).toBeInTheDocument()
    expect(screen.getByText('Operational Day')).toBeInTheDocument()
    expect(screen.getByText('2026-09-23')).toBeInTheDocument()
  })

  it('shows On time with no deviation figure when the doctor is on schedule', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({
      sessionId: SESSION_ID,
      applicable: true,
      status: 'ON_TIME',
      currentPatientOrdinal: 3,
      expectedPatientOrdinal: 3,
      deviationMinutes: null,
      firstSlotTime: '09:00:00',
      operationalDay: '2026-09-23',
    })

    render(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByText('On time')).toBeInTheDocument()
    expect(screen.queryByText(/min late/)).not.toBeInTheDocument()
    expect(screen.queryByText(/min early/)).not.toBeInTheDocument()
  })

  it('shows Running early with a minutes-early figure', async () => {
    mockedGetAsStaff.mockResolvedValueOnce({
      sessionId: SESSION_ID,
      applicable: true,
      status: 'RUNNING_EARLY',
      currentPatientOrdinal: 4,
      expectedPatientOrdinal: 1,
      deviationMinutes: 45,
      firstSlotTime: '09:00:00',
      operationalDay: '2026-09-23',
    })

    render(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByText('Running early')).toBeInTheDocument()
    expect(screen.getByText(/45 min early/)).toBeInTheDocument()
  })

  it('re-fetches when refreshKey changes', async () => {
    mockedGetAsStaff.mockResolvedValue({
      sessionId: SESSION_ID,
      applicable: true,
      status: 'ON_TIME',
      currentPatientOrdinal: 1,
      expectedPatientOrdinal: 1,
      deviationMinutes: null,
      firstSlotTime: '09:00:00',
      operationalDay: '2026-09-23',
    })

    const { rerender } = render(
      <LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} refreshKey={1} />,
    )
    await screen.findByText('On time')
    expect(mockedGetAsStaff).toHaveBeenCalledTimes(1)

    rerender(<LiveScheduleStatusIndicator mode="staff" clinicId={CLINIC_ID} sessionId={SESSION_ID} refreshKey={2} />)

    expect(mockedGetAsStaff).toHaveBeenCalledTimes(2)
  })
})

describe('LiveScheduleStatusIndicator (patient mode)', () => {
  beforeEach(() => {
    mockedGetAsPatient.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it('shows a loading state before the first fetch resolves', () => {
    mockedGetAsPatient.mockReturnValueOnce(new Promise(() => {})) // never resolves during this test

    render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    expect(screen.getByRole('status')).toHaveTextContent(/checking schedule status/i)
  })

  it('shows an error message when the fetch fails, distinct from not-applicable', async () => {
    mockedGetAsPatient.mockRejectedValueOnce(new Error('network error'))

    render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't load the schedule status/i)
  })

  it('renders nothing once the response is not applicable for a booking', async () => {
    mockedGetAsPatient.mockResolvedValueOnce({
      bookingId: BOOKING_ID,
      applicable: false,
      doctorName: null,
      currentPatientOrdinal: null,
      statusText: null,
      estimatedWaitMinutes: null,
    })

    const { container } = render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  it('renders doctor name, current-patient ordinal, plain-language status text, and estimated wait', async () => {
    mockedGetAsPatient.mockResolvedValueOnce({
      bookingId: BOOKING_ID,
      applicable: true,
      doctorName: 'Dr. Asha Rao',
      currentPatientOrdinal: 1,
      statusText: '15 min delayed',
      estimatedWaitMinutes: 20,
    })

    render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Dr. Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Currently Seeing')).toBeInTheDocument()
    expect(screen.getByText('Patient 1')).toBeInTheDocument()
    expect(screen.getByText('15 min delayed')).toBeInTheDocument()
    expect(screen.getByText('Estimated Wait')).toBeInTheDocument()
    expect(screen.getByText('20 min')).toBeInTheDocument()
  })

  it('shows no estimated wait once the patient has already been seen', async () => {
    mockedGetAsPatient.mockResolvedValueOnce({
      bookingId: BOOKING_ID,
      applicable: true,
      doctorName: 'Dr. Asha Rao',
      currentPatientOrdinal: null,
      statusText: 'Visit complete',
      estimatedWaitMinutes: null,
    })

    render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Visit complete')).toBeInTheDocument()
    expect(screen.queryByText('Estimated Wait')).not.toBeInTheDocument()
  })

  it('never renders a field beyond the patient-safe set (no raw status code, no other patient data)', async () => {
    mockedGetAsPatient.mockResolvedValueOnce({
      bookingId: BOOKING_ID,
      applicable: true,
      doctorName: 'Dr. Asha Rao',
      currentPatientOrdinal: 1,
      statusText: 'On time',
      estimatedWaitMinutes: 5,
    })

    render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} />)

    await screen.findByText('Dr. Asha Rao')
    expect(screen.queryByText(/ON_TIME/)).not.toBeInTheDocument()
    expect(screen.queryByText('Expected Patient')).not.toBeInTheDocument()
    expect(screen.queryByText('Operational Day')).not.toBeInTheDocument()
  })

  it('re-fetches when refreshKey changes', async () => {
    mockedGetAsPatient.mockResolvedValue({
      bookingId: BOOKING_ID,
      applicable: true,
      doctorName: 'Dr. Asha Rao',
      currentPatientOrdinal: 1,
      statusText: 'On time',
      estimatedWaitMinutes: 5,
    })

    const { rerender } = render(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} refreshKey={1} />)
    await screen.findByText('Dr. Asha Rao')
    expect(mockedGetAsPatient).toHaveBeenCalledTimes(1)

    rerender(<LiveScheduleStatusIndicator mode="patient" bookingId={BOOKING_ID} refreshKey={2} />)

    expect(mockedGetAsPatient).toHaveBeenCalledTimes(2)
  })
})
