import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { JoinWaitlistForm } from '../../src/features/waitlist/JoinWaitlistForm'
import { joinWaitlist, WaitlistJoinApiError } from '../../src/features/waitlist/api'
import { listPatientClinicDoctors } from '../../src/features/doctor-picker/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/waitlist/api')>(
    '../../src/features/waitlist/api',
  )
  return {
    ...actual,
    joinWaitlist: vi.fn(),
  }
})

vi.mock('../../src/features/doctor-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-picker/api')>(
    '../../src/features/doctor-picker/api',
  )
  return {
    ...actual,
    listPatientClinicDoctors: vi.fn(),
  }
})

const mockedJoinWaitlist = vi.mocked(joinWaitlist)
const mockedListPatientClinicDoctors = vi.mocked(listPatientClinicDoctors)

const CLINIC_ID = 'clinic-1'

describe('JoinWaitlistForm', () => {
  beforeEach(() => {
    mockedJoinWaitlist.mockReset()
    mockedListPatientClinicDoctors.mockReset()
    mockedListPatientClinicDoctors.mockResolvedValue({
      doctors: [
        { doctorProfileId: 'doctor-1', name: 'Dr. Priya Nair', specialization: 'General Medicine', experienceYears: 4 },
        { doctorProfileId: 'doctor-2', name: 'Dr. Priya Nair', specialization: 'General Medicine', experienceYears: 10 },
      ],
      page: 0,
      pageSize: 500,
      totalCount: 2,
    })
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  // staff-console-audit-2026-09-10-style P0 fix, patient side: the raw free-text "Doctor" UUID
  // field is gone - patients pick from the clinic's actual doctor list, distinguishable by
  // specialization/experience (two doctors here share a name, the exact live case at Star Clinic).
  it('joins for a specific doctor picked from the clinic doctor list', async () => {
    const user = userEvent.setup()
    mockedJoinWaitlist.mockResolvedValueOnce({
      id: 'entry-1',
      clinicId: CLINIC_ID,
      doctorProfileId: 'doctor-2',
      specialization: null,
      status: 'WAITING',
      joinedAt: '2026-09-04T10:00:00Z',
    })

    render(<JoinWaitlistForm clinicId={CLINIC_ID} />)

    await screen.findByRole('option', { name: /dr\. priya nair — general medicine, 10 yrs/i })
    await user.selectOptions(screen.getByLabelText('Doctor'), 'doctor-2')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByText(/you're on the waitlist/i)).toBeInTheDocument()
    expect(mockedJoinWaitlist).toHaveBeenCalledWith(CLINIC_ID, { doctorProfileId: 'doctor-2' }, 'a.jwt.token')
  })

  it('joins for a specialization', async () => {
    const user = userEvent.setup()
    mockedJoinWaitlist.mockResolvedValueOnce({
      id: 'entry-2',
      clinicId: CLINIC_ID,
      doctorProfileId: null,
      specialization: 'Cardiology',
      status: 'WAITING',
      joinedAt: '2026-09-04T10:00:00Z',
    })

    render(<JoinWaitlistForm clinicId={CLINIC_ID} />)

    await user.click(screen.getByLabelText(/any doctor with a specialization/i))
    await user.type(screen.getByLabelText('Specialization'), 'Cardiology')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByText(/you're on the waitlist/i)).toBeInTheDocument()
    expect(screen.getByText(/for cardiology/i)).toBeInTheDocument()
    expect(mockedJoinWaitlist).toHaveBeenCalledWith(CLINIC_ID, { specialization: 'Cardiology' }, 'a.jwt.token')
  })

  // _diagnostics [MAJOR] - [full-repo-audit] - [SILENT_EMPTY_SELECT]
  it('shows a helpful message instead of a silently unfillable select when the clinic has no doctors', async () => {
    mockedListPatientClinicDoctors.mockReset().mockResolvedValue({ doctors: [], page: 0, pageSize: 500, totalCount: 0 })

    render(<JoinWaitlistForm clinicId={CLINIC_ID} />)

    expect(await screen.findByText(/this clinic has no doctors listed yet/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Doctor')).not.toBeInTheDocument()
  })

  it('shows the WAITLIST_TARGET_REQUIRED error message', async () => {
    const user = userEvent.setup()
    mockedJoinWaitlist.mockRejectedValueOnce(new WaitlistJoinApiError({ error: 'WAITLIST_TARGET_REQUIRED' }))

    render(<JoinWaitlistForm clinicId={CLINIC_ID} />)

    await screen.findByRole('option', { name: /dr\. priya nair — general medicine, 4 yrs/i })
    await user.selectOptions(screen.getByLabelText('Doctor'), 'doctor-1')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/choose either a doctor or a specialization/i)
  })

  it('shows the DOCTOR_NOT_STAFFED_AT_CLINIC error message', async () => {
    const user = userEvent.setup()
    mockedJoinWaitlist.mockRejectedValueOnce(new WaitlistJoinApiError({ error: 'DOCTOR_NOT_STAFFED_AT_CLINIC' }))

    render(<JoinWaitlistForm clinicId={CLINIC_ID} />)

    await screen.findByRole('option', { name: /dr\. priya nair — general medicine, 4 yrs/i })
    await user.selectOptions(screen.getByLabelText('Doctor'), 'doctor-1')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/not staffed at this clinic/i)
  })
})
