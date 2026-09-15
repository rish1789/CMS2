import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WalkInForm } from '../../src/features/staff-booking/WalkInForm'
import { insertWalkIn, WalkInApiError } from '../../src/features/staff-booking/api'
import { listAppointmentTypes } from '../../src/features/appointment-types/api'
import { searchPatients } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/staff-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-booking/api')>(
    '../../src/features/staff-booking/api',
  )
  return {
    ...actual,
    insertWalkIn: vi.fn(),
  }
})

vi.mock('../../src/features/appointment-types/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
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

const mockedInsertWalkIn = vi.mocked(insertWalkIn)
const mockedListAppointmentTypes = vi.mocked(listAppointmentTypes)
const mockedSearchPatients = vi.mocked(searchPatients)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'
const DOCTOR_PROFILE_ID = 'doctor-1'

// staff-console-audit-2026-09-10 P0: the raw "Patient ID" free-text field is gone - staff
// search by name/phone and pick from live results, matching the front-desk's actual workflow.
async function pickExistingPatient(user: ReturnType<typeof userEvent.setup>) {
  // { selector: 'input' } disambiguates from the "Patient" <legend> labelling the radio fieldset.
  await user.type(screen.getByLabelText(/^patient$/i, { selector: 'input' }), 'Asha')
  await screen.findByRole('option', { name: /asha rao/i })
  await user.click(screen.getByRole('option', { name: /asha rao/i }))
}

async function selectAppointmentType(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('option', { name: /general consult/i })
  await user.selectOptions(screen.getByLabelText(/appointment type/i), 'type-1')
}

describe('WalkInForm', () => {
  beforeEach(() => {
    mockedInsertWalkIn.mockReset()
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

  function renderForm() {
    render(<WalkInForm clinicId={CLINIC_ID} sessionId={SESSION_ID} doctorProfileId={DOCTOR_PROFILE_ID} />)
  }

  it('inserts a walk-in for an existing patient found by search and shows the locked fee', async () => {
    const user = userEvent.setup()
    mockedInsertWalkIn.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: 'slot-1',
      patientId: 'patient-1',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-04T10:00:00Z',
    })

    renderForm()

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByText(/walk-in inserted/i)).toBeInTheDocument()
    expect(screen.getByText(/300\.00/)).toBeInTheDocument()
    expect(mockedInsertWalkIn).toHaveBeenCalledWith(
      CLINIC_ID,
      SESSION_ID,
      { patientId: 'patient-1', appointmentTypeId: 'type-1', overrideReason: undefined },
      'a.jwt.token',
    )
  })

  it('submits new-walk-in-patient fields when that mode is selected', async () => {
    const user = userEvent.setup()
    mockedInsertWalkIn.mockResolvedValueOnce({
      id: 'booking-2',
      slotId: 'slot-2',
      patientId: 'patient-2',
      appointmentTypeId: 'type-1',
      lockedFee: 500,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-04T10:00:00Z',
    })

    renderForm()

    await user.click(screen.getByLabelText(/new walk-in patient/i))
    await user.type(screen.getByLabelText(/patient name/i), 'Walk-in Patient')
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByText(/walk-in inserted/i)).toBeInTheDocument()
    expect(mockedInsertWalkIn).toHaveBeenCalledWith(
      CLINIC_ID,
      SESSION_ID,
      { patientName: 'Walk-in Patient', patientPhone: undefined, appointmentTypeId: 'type-1', overrideReason: undefined },
      'a.jwt.token',
    )
  })

  it('shows the override reason as required after OVERRIDE_REASON_REQUIRED, then lets the user resubmit with one', async () => {
    const user = userEvent.setup()
    mockedInsertWalkIn.mockRejectedValueOnce(new WalkInApiError({ error: 'OVERRIDE_REASON_REQUIRED' }))
    mockedInsertWalkIn.mockResolvedValueOnce({
      id: 'booking-3',
      slotId: 'slot-3',
      patientId: 'patient-1',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-04T10:00:00Z',
    })

    renderForm()

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/override reason is required/i)
    expect(screen.getByLabelText(/override reason/i)).toBeRequired()

    await user.type(screen.getByLabelText(/override reason/i), 'Family emergency')
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByText(/walk-in inserted/i)).toBeInTheDocument()
  })

  it('shows the NO_SLOT_AVAILABLE error message', async () => {
    const user = userEvent.setup()
    mockedInsertWalkIn.mockRejectedValueOnce(new WalkInApiError({ error: 'NO_SLOT_AVAILABLE' }))

    renderForm()

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no slot is available/i)
  })

  it('shows the NO_FEE_CONFIGURED error message', async () => {
    const user = userEvent.setup()
    mockedInsertWalkIn.mockRejectedValueOnce(new WalkInApiError({ error: 'NO_FEE_CONFIGURED' }))

    renderForm()

    await pickExistingPatient(user)
    await selectAppointmentType(user)
    await user.click(screen.getByRole('button', { name: /insert walk-in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no fee is configured/i)
  })

  it('shows a helpful message instead of a silently unfillable select when the doctor has no appointment types', async () => {
    mockedListAppointmentTypes.mockReset()
    mockedListAppointmentTypes.mockResolvedValue([])
    const user = userEvent.setup()

    renderForm()
    await pickExistingPatient(user)

    expect(await screen.findByText(/no appointment types configured yet/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/appointment type/i)).not.toBeInTheDocument()
  })
})
