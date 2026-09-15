import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueueBookSlotForm } from '../../src/features/patient-booking/QueueBookSlotForm'
import { bookQueueSlot, QueueBookSlotApiError } from '../../src/features/patient-booking/queueApi'
import { storePatientSession } from '../../src/features/patient-account/token'
import type { AppointmentTypeOption } from '../../src/features/patient-booking/api'

vi.mock('../../src/features/patient-booking/queueApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../src/features/patient-booking/queueApi')>(
      '../../src/features/patient-booking/queueApi',
    )
  return {
    ...actual,
    bookQueueSlot: vi.fn(),
  }
})

const mockedBookQueueSlot = vi.mocked(bookQueueSlot)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

const APPOINTMENT_TYPES: AppointmentTypeOption[] = [
  { id: 'type-1', doctorProfileId: 'doctor-1', name: 'General consultation', feeOverride: null },
]

describe('QueueBookSlotForm (patient)', () => {
  beforeEach(() => {
    mockedBookQueueSlot.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'acct-1', email: 'patient@example.com' })
  })

  it('books into the queue and shows the assigned token number', async () => {
    const user = userEvent.setup()
    mockedBookQueueSlot.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: 'slot-1',
      tokenNumber: 3,
      patientId: 'patient-1',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-03T10:00:00Z',
    })

    render(
      <QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} appointmentTypes={APPOINTMENT_TYPES} />,
      { wrapper: MemoryRouter },
    )

    await user.type(screen.getByLabelText(/your name/i), 'Jane Doe')
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
    await user.click(screen.getByRole('button', { name: /book into queue/i }))

    expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
    expect(screen.getByText('Token number')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(mockedBookQueueSlot).toHaveBeenCalledWith(
      CLINIC_ID,
      SESSION_ID,
      { patientName: 'Jane Doe', appointmentTypeId: 'type-1' },
      'a.jwt.token',
    )
  })

  it('shows the NO_FEE_CONFIGURED error message', async () => {
    const user = userEvent.setup()
    mockedBookQueueSlot.mockRejectedValueOnce(new QueueBookSlotApiError({ error: 'NO_FEE_CONFIGURED' }))

    render(
      <QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} appointmentTypes={APPOINTMENT_TYPES} />,
      { wrapper: MemoryRouter },
    )

    await user.type(screen.getByLabelText(/your name/i), 'Jane Doe')
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
    await user.click(screen.getByRole('button', { name: /book into queue/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no fee is configured/i)
  })

  it('shows a message directing back to the session picker when no appointment types are available', () => {
    render(<QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />, { wrapper: MemoryRouter })

    expect(screen.getByText(/pick a session from your clinic's queue list/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse queue sessions/i })).toHaveAttribute(
      'href',
      `/patient/clinics/${CLINIC_ID}/queue-sessions`,
    )
  })

  // patient-booking-visual-polish: doctorName/sessionDate/startTime/endTime ride along from
  // QueueSessionList via router state - when present, the form shows what it's booking instead
  // of a bare "your name" field with no context.
  it('shows a summary panel when session detail is passed in', () => {
    render(
      <QueueBookSlotForm
        clinicId={CLINIC_ID}
        sessionId={SESSION_ID}
        appointmentTypes={APPOINTMENT_TYPES}
        doctorName="Dr. Asha Rao"
        sessionDate="2026-09-14"
        startTime="16:00:00"
        endTime="18:00:00"
      />,
      { wrapper: MemoryRouter },
    )

    expect(screen.getByText('Dr. Asha Rao')).toBeInTheDocument()
    expect(screen.getByText(/16:00–18:00/)).toBeInTheDocument()
  })

  it('omits the summary panel when session detail is absent (a direct/refreshed visit)', () => {
    render(
      <QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} appointmentTypes={APPOINTMENT_TYPES} />,
      { wrapper: MemoryRouter },
    )

    expect(screen.queryByText(/16:00–18:00/)).not.toBeInTheDocument()
  })
})
