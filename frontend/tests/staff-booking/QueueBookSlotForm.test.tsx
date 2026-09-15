import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueueBookSlotForm } from '../../src/features/staff-booking/QueueBookSlotForm'
import { bookQueueSlot, QueueBookSlotApiError } from '../../src/features/staff-booking/queueApi'
import { listAppointmentTypes } from '../../src/features/appointment-types/api'
import { searchPatients } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/staff-booking/queueApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../src/features/staff-booking/queueApi')>(
      '../../src/features/staff-booking/queueApi',
    )
  return {
    ...actual,
    bookQueueSlot: vi.fn(),
  }
})

vi.mock('../../src/features/appointment-types/api', async () => {
  const actual =
    await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
      '../../src/features/appointment-types/api',
    )
  return {
    ...actual,
    listAppointmentTypes: vi.fn(),
  }
})

vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return {
    ...actual,
    searchPatients: vi.fn(),
  }
})

const mockedBookQueueSlot = vi.mocked(bookQueueSlot)
const mockedListAppointmentTypes = vi.mocked(listAppointmentTypes)
const mockedSearchPatients = vi.mocked(searchPatients)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'
const DOCTOR_PROFILE_ID = 'doctor-1'

async function pickExistingPatient(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^patient$/i, { selector: 'input' }), 'Asha')
  await screen.findByRole('option', { name: /asha rao/i })
  await user.click(screen.getByRole('option', { name: /asha rao/i }))
}

describe('QueueBookSlotForm (staff)', () => {
  beforeEach(() => {
    mockedBookQueueSlot.mockReset()
    mockedListAppointmentTypes.mockReset()
    mockedListAppointmentTypes.mockResolvedValue([
      { id: 'type-1', doctorProfileId: DOCTOR_PROFILE_ID, name: 'General consult', feeOverride: null },
    ])
    mockedSearchPatients.mockReset()
    mockedSearchPatients.mockResolvedValue({
      patients: [{ patientId: 'patient-1', name: 'Asha Rao', phone: '9876543210' }],
      page: 0,
      pageSize: 8,
      totalCount: 1,
    })
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('books an existing patient found by search and shows the assigned token number', async () => {
    const user = userEvent.setup()
    mockedBookQueueSlot.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: 'slot-1',
      tokenNumber: 7,
      patientId: 'patient-1',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-03T10:00:00Z',
    })

    render(<QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await pickExistingPatient(user)
    await screen.findByRole('option', { name: /general consult/i })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
    await user.click(screen.getByRole('button', { name: /book into queue/i }))

    expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
    expect(screen.getByText(/token number: 7/i)).toBeInTheDocument()
    expect(mockedBookQueueSlot).toHaveBeenCalledWith(
      CLINIC_ID,
      SESSION_ID,
      { patientId: 'patient-1', appointmentTypeId: 'type-1' },
      'a.jwt.token',
    )
  })

  it('shows the NOT_A_QUEUE_SESSION error message', async () => {
    const user = userEvent.setup()
    mockedBookQueueSlot.mockRejectedValueOnce(new QueueBookSlotApiError({ error: 'NOT_A_QUEUE_SESSION' }))

    render(<QueueBookSlotForm clinicId={CLINIC_ID} sessionId={SESSION_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await pickExistingPatient(user)
    await screen.findByRole('option', { name: /general consult/i })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
    await user.click(screen.getByRole('button', { name: /book into queue/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/not a queue\/token session/i)
  })
})
