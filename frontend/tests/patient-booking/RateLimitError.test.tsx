// 060-booking-abuse-prevention T035: a 429 RATE_LIMITED response must render the cooldown
// message including the approximate wait time derived from retryAfterSeconds (spec.md FR-011).
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BookSlotForm } from '../../src/features/patient-booking/BookSlotForm'
import { bookSlot, type OpenSlot } from '../../src/features/patient-booking/api'
import { ApiError } from '../../src/lib/apiClient'
import { QueueBookSlotForm } from '../../src/features/patient-booking/QueueBookSlotForm'
import { bookQueueSlot, QueueBookSlotApiError } from '../../src/features/patient-booking/queueApi'
import { storePatientSession } from '../../src/features/patient-account/token'
import type { AppointmentTypeOption } from '../../src/features/patient-booking/api'

vi.mock('../../src/features/patient-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-booking/api')>(
    '../../src/features/patient-booking/api',
  )
  return {
    ...actual,
    bookSlot: vi.fn(),
  }
})

vi.mock('../../src/features/patient-booking/queueApi', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-booking/queueApi')>(
    '../../src/features/patient-booking/queueApi',
  )
  return {
    ...actual,
    bookQueueSlot: vi.fn(),
  }
})

const mockedBookSlot = vi.mocked(bookSlot)
const mockedBookQueueSlot = vi.mocked(bookQueueSlot)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

const SLOT: OpenSlot = {
  slotId: 'slot-1',
  doctorProfileId: 'doc-1',
  doctorName: 'Dr. Asha Rao',
  sessionDate: '2026-09-10',
  startTime: '09:00:00',
  endTime: '09:15:00',
  appointmentTypes: [{ id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', fee: null }],
}

const APPOINTMENT_TYPES: AppointmentTypeOption[] = [
  { id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', fee: null },
]

describe('RATE_LIMITED error surfacing', () => {
  beforeEach(() => {
    mockedBookSlot.mockReset()
    mockedBookQueueSlot.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'acct-1', email: 'patient@example.com' })
  })

  it('BookSlotForm shows the cooldown message with the approximate wait time', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(429, 'Too many booking attempts - please wait before trying again', {
        error: 'RATE_LIMITED',
        retryAfterSeconds: 300,
      }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many booking attempts/i)
    expect(alert).toHaveTextContent(/about 5 minutes/i)
  })

  it('BookSlotForm shows a generic cooldown message when retryAfterSeconds is absent', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(429, 'Too many booking attempts - please wait before trying again', { error: 'RATE_LIMITED' }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many booking attempts/i)
  })

  it('QueueBookSlotForm shows the cooldown message with the approximate wait time', async () => {
    const user = userEvent.setup()
    mockedBookQueueSlot.mockRejectedValueOnce(
      new QueueBookSlotApiError({
        error: 'RATE_LIMITED',
        message: 'Too many booking attempts - please wait before trying again',
        retryAfterSeconds: 90,
      }),
    )

    render(
      <QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} onClose={vi.fn()} appointmentTypes={APPOINTMENT_TYPES} />,
      { wrapper: MemoryRouter },
    )

    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
    await user.click(screen.getByRole('button', { name: /book into queue/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many booking attempts/i)
    expect(alert).toHaveTextContent(/about 2 minutes/i)
  })
})
