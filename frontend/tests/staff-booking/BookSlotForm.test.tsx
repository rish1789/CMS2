import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BookSlotForm } from '../../src/features/staff-booking/BookSlotForm'
import { bookSlot } from '../../src/features/staff-booking/api'
import { ApiError } from '../../src/lib/apiClient'
import { listAppointmentTypes } from '../../src/features/appointment-types/api'
import { searchPatients } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/staff-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-booking/api')>(
    '../../src/features/staff-booking/api',
  )
  return {
    ...actual,
    bookSlot: vi.fn(),
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

const mockedBookSlot = vi.mocked(bookSlot)
const mockedListAppointmentTypes = vi.mocked(listAppointmentTypes)
const mockedSearchPatients = vi.mocked(searchPatients)

const CLINIC_ID = 'clinic-1'
const SLOT_ID = 'slot-1'
const DOCTOR_PROFILE_ID = 'doctor-1'

async function pickExistingPatient(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^patient$/i, { selector: 'input' }), 'Asha')
  await screen.findByRole('option', { name: /asha rao/i })
  await user.click(screen.getByRole('option', { name: /asha rao/i }))
}

async function selectAppointmentType(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('option', { name: /general consult/i })
  await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
}

describe('BookSlotForm', () => {
  beforeEach(() => {
    mockedBookSlot.mockReset()
    mockedListAppointmentTypes.mockReset()
    mockedListAppointmentTypes.mockResolvedValue([
      { id: 'type-1', doctorProfileId: DOCTOR_PROFILE_ID, name: 'General consult', fee: null },
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

  it('books an existing patient found by search and shows the locked fee', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: SLOT_ID,
      patientId: 'patient-1',
      patientName: 'Asha Rao',
      doctorName: 'Dr. Kavita Iyer',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-03T10:00:00Z',
    })

    render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
    expect(screen.getByText(/300\.00/)).toBeInTheDocument()
    expect(mockedBookSlot).toHaveBeenCalledWith(
      CLINIC_ID,
      SLOT_ID,
      { patientId: 'patient-1', appointmentTypeId: 'type-1' },
      'a.jwt.token',
    )
  })

  it('shows the SLOT_ALREADY_BOOKED error message', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(409, 'This slot is already booked.', { error: 'SLOT_ALREADY_BOOKED' }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already booked/i)
  })

  it('submits new-walk-in-patient fields when that mode is selected', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockResolvedValueOnce({
      id: 'booking-2',
      slotId: SLOT_ID,
      patientId: 'patient-2',
      patientName: 'Walk-in Patient',
      doctorName: 'Dr. Kavita Iyer',
      appointmentTypeId: 'type-1',
      lockedFee: 500,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-03T10:00:00Z',
    })

    render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await user.click(screen.getByLabelText(/new walk-in patient/i))
    await user.type(screen.getByLabelText(/patient name/i), 'Walk-in Patient')
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
    expect(mockedBookSlot).toHaveBeenCalledWith(
      CLINIC_ID,
      SLOT_ID,
      { patientName: 'Walk-in Patient', patientPhone: undefined, appointmentTypeId: 'type-1' },
      'a.jwt.token',
    )
  })

  it('shows a helpful message instead of a silently unfillable select when the doctor has no appointment types', async () => {
    mockedListAppointmentTypes.mockReset()
    mockedListAppointmentTypes.mockResolvedValue([])
    const user = userEvent.setup()

    render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)
    await pickExistingPatient(user)

    expect(await screen.findByText(/no appointment types configured yet/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/appointment type/i)).not.toBeInTheDocument()
  })

  // 054-forms-validation-consistency T014: pre-submit required-field check blocks the network
  // call entirely (research.md Decision 3) - the picked patient survives the failed attempt.
  it('blocks submission and shows an inline error when no appointment type is selected', async () => {
    const user = userEvent.setup()

    render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)

    await pickExistingPatient(user)
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/please select an appointment type/i)
    expect(mockedBookSlot).not.toHaveBeenCalled()
    expect(screen.getByText('Asha Rao')).toBeInTheDocument()
  })

  // 074-duplicate-patient-phone (PB-001): the real reason, the typed values kept, and an explicit
  // way to book the existing patient instead - never a silent merge.
  describe('when the new patient\'s phone already belongs to a patient here', () => {
    async function submitNewPatient(user: ReturnType<typeof userEvent.setup>) {
      render(<BookSlotForm clinicId={CLINIC_ID} slotId={SLOT_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)
      await user.click(screen.getByLabelText(/new walk-in patient/i))
      await user.type(screen.getByLabelText(/patient name/i), 'New Person')
      await user.type(screen.getByLabelText(/phone/i), '9876543210')
      await selectAppointmentType(user)
      await user.click(screen.getByRole('button', { name: /book slot/i }))
    }

    it('names the existing patient, keeps the typed values, and books them on request', async () => {
      const user = userEvent.setup()
      mockedBookSlot.mockRejectedValueOnce(
        new ApiError(409, 'A patient with this phone number is already registered at this clinic.', {
          error: 'PATIENT_PHONE_ALREADY_REGISTERED',
          message: 'A patient with this phone number is already registered at this clinic.',
          existingPatient: { id: 'patient-9', name: 'Asha Rao' },
        }),
      )
      mockedBookSlot.mockResolvedValueOnce({
        id: 'booking-9',
        slotId: SLOT_ID,
        patientId: 'patient-9',
        patientName: 'Asha Rao',
        doctorName: 'Dr. Kavita Iyer',
        appointmentTypeId: 'type-1',
        lockedFee: 500,
        paymentStatus: 'PENDING',
        createdAt: '2026-10-01T10:00:00Z',
      })

      await submitNewPatient(user)

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent(/already registered at this clinic/i)
      expect(alert).toHaveTextContent('Asha Rao')
      expect(screen.queryByText(/slot is already booked/i)).not.toBeInTheDocument()
      expect(screen.getByLabelText(/patient name/i)).toHaveValue('New Person')
      expect(screen.getByLabelText(/phone/i)).toHaveValue('9876543210')

      await user.click(within(alert).getByRole('button', { name: /book asha rao instead/i }))

      expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
      expect(mockedBookSlot).toHaveBeenLastCalledWith(
        CLINIC_ID,
        SLOT_ID,
        { patientId: 'patient-9', appointmentTypeId: 'type-1' },
        'a.jwt.token',
      )
    })

    it('suggests searching when the existing patient could not be named', async () => {
      const user = userEvent.setup()
      mockedBookSlot.mockRejectedValueOnce(
        new ApiError(409, 'A patient with this phone number is already registered at this clinic.', {
          error: 'PATIENT_PHONE_ALREADY_REGISTERED',
          existingPatient: null,
        }),
      )

      await submitNewPatient(user)

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent(/search for them under existing patient/i)
      expect(within(alert).queryByRole('button')).not.toBeInTheDocument()
    })
  })
})
