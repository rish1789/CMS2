import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StaffJoinWaitlistForm } from '../../src/features/waitlist/StaffJoinWaitlistForm'
import { staffJoinWaitlist } from '../../src/features/waitlist/api'
import { ApiError } from '../../src/lib/apiClient'
import { listClinicDoctors } from '../../src/features/doctor-picker/api'
import { searchPatients } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/waitlist/api')>(
    '../../src/features/waitlist/api',
  )
  return {
    ...actual,
    staffJoinWaitlist: vi.fn(),
  }
})

vi.mock('../../src/features/doctor-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-picker/api')>(
    '../../src/features/doctor-picker/api',
  )
  return {
    ...actual,
    listClinicDoctors: vi.fn(),
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

const mockedStaffJoinWaitlist = vi.mocked(staffJoinWaitlist)
const mockedListClinicDoctors = vi.mocked(listClinicDoctors)
const mockedSearchPatients = vi.mocked(searchPatients)

const CLINIC_ID = 'clinic-1'

// 2026-09-16 real bug found live: this form's "Patient account" field was a raw free-text UUID
// input - a staff member naturally typed the patient's name ("Rajesh Kumar") into it, which
// fell through to the backend's generic "The request could not be processed." fallback (a
// malformed-UUID JSON binding failure no specific exception handler caught). Fixed onto
// PatientPicker (search by name/phone, then select) - the same fix already applied to
// WalkInForm/BookSlotForm for this exact bug class - and gated on the selected patient actually
// having a linked account, since a walk-in-only Patient record may not have one.
async function pickPatient(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.type(screen.getByLabelText(/^patient$/i, { selector: 'input' }), name.split(' ')[0])
  await screen.findByRole('option', { name: new RegExp(name, 'i') })
  await user.click(screen.getByRole('option', { name: new RegExp(name, 'i') }))
}

describe('StaffJoinWaitlistForm', () => {
  beforeEach(() => {
    mockedStaffJoinWaitlist.mockReset()
    mockedListClinicDoctors.mockReset()
    mockedListClinicDoctors.mockResolvedValue({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', staffCode: 'DR-1001', specialization: 'General Medicine' },
        { doctorProfileId: 'doctor-2', name: 'Dr. Arjun Rao', staffCode: 'DR-1002', specialization: 'Pediatrics' },
      ],
      page: 0,
      pageSize: 500,
      totalCount: 2,
    })
    mockedSearchPatients.mockReset()
    mockedSearchPatients.mockResolvedValue({
      patients: [
        { patientId: 'patient-1', name: 'Asha Rao', phone: '9876543210', anonymizedAt: null, patientAccountId: 'account-1' },
      ],
      page: 0,
      pageSize: 8,
      totalCount: 1,
    })
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  // staff-console-audit-2026-09-10 P0: the raw free-text "Doctor" UUID field is gone - staff
  // pick from the clinic's actual doctor list, distinguishable by staff code (two doctors here
  // share a name, same real-world case the audit flagged on the Doctors page).
  it('joins a patient to the waitlist for a doctor picked from the clinic doctor list', async () => {
    const user = userEvent.setup()
    mockedStaffJoinWaitlist.mockResolvedValueOnce({
      id: 'entry-1',
      clinicId: CLINIC_ID,
      doctorProfileId: 'doctor-2',
      specialization: null,
      status: 'WAITING',
      joinedAt: '2026-09-10T10:00:00Z',
      offeredAt: null,
      offerExpiresAt: null,
    })

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)

    await pickPatient(user, 'Asha Rao')
    await screen.findByRole('option', { name: /dr\. arjun rao — dr-1002/i })
    await user.selectOptions(screen.getByLabelText(/^doctor$/i), 'doctor-2')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByText(/added to the waitlist/i)).toBeInTheDocument()
    expect(mockedStaffJoinWaitlist).toHaveBeenCalledWith(
      CLINIC_ID,
      { patientAccountId: 'account-1', doctorProfileId: 'doctor-2' },
      'a.jwt.token',
    )
  })

  it('joins a patient to the waitlist for a specialization', async () => {
    const user = userEvent.setup()
    mockedStaffJoinWaitlist.mockResolvedValueOnce({
      id: 'entry-2',
      clinicId: CLINIC_ID,
      doctorProfileId: null,
      specialization: 'Cardiology',
      status: 'WAITING',
      joinedAt: '2026-09-10T10:00:00Z',
      offeredAt: null,
      offerExpiresAt: null,
    })

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)

    await pickPatient(user, 'Asha Rao')
    await user.click(screen.getByLabelText(/any doctor with a specialization/i))
    await user.type(screen.getByLabelText(/^specialization$/i, { selector: 'input' }), 'Cardiology')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByText(/added to the waitlist for cardiology/i)).toBeInTheDocument()
    expect(mockedStaffJoinWaitlist).toHaveBeenCalledWith(
      CLINIC_ID,
      { patientAccountId: 'account-1', specialization: 'Cardiology' },
      'a.jwt.token',
    )
  })

  // The actual bug: a patient found by search with no linked account can no longer reach the
  // submit call at all (previously: a raw text field let staff submit any string, including a
  // typed name, as the "patientAccountId").
  it("disables submit and explains when the selected patient has no online account", async () => {
    mockedSearchPatients.mockReset().mockResolvedValue({
      patients: [{ patientId: 'patient-2', name: 'Rajesh Kumar', phone: '9123456780', anonymizedAt: null, patientAccountId: null }],
      page: 0,
      pageSize: 8,
      totalCount: 1,
    })
    const user = userEvent.setup()

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)
    await pickPatient(user, 'Rajesh Kumar')

    expect(screen.getByText(/doesn't have an online account yet/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /join waitlist/i })).toBeDisabled()
    expect(mockedStaffJoinWaitlist).not.toHaveBeenCalled()
  })

  // _diagnostics [MAJOR] - [full-repo-audit] - [SILENT_EMPTY_SELECT]
  it('shows a helpful message instead of a silently unfillable select when the clinic has no doctors', async () => {
    mockedListClinicDoctors.mockReset().mockResolvedValue({ doctors: [], page: 0, pageSize: 500, totalCount: 0 })
    const user = userEvent.setup()

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)
    await pickPatient(user, 'Asha Rao')

    expect(await screen.findByText(/no doctors are staffed at this clinic yet/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/^doctor$/i)).not.toBeInTheDocument()
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedStaffJoinWaitlist.mockRejectedValueOnce(
      new ApiError(403, 'Only front-desk Operations staff or a ClinicAdmin can join a patient to the waitlist.', {
        error: 'FORBIDDEN',
      }),
    )

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)

    await pickPatient(user, 'Asha Rao')
    await screen.findByRole('option', { name: /dr\. priya nair — dr-1001/i })
    await user.selectOptions(screen.getByLabelText(/^doctor$/i), 'doctor-1')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff/i)
  })
})
