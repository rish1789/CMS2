import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StaffJoinWaitlistForm } from '../../src/features/waitlist/StaffJoinWaitlistForm'
import { staffJoinWaitlist, WaitlistJoinApiError } from '../../src/features/waitlist/api'
import { listClinicDoctors } from '../../src/features/doctor-picker/api'
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

const mockedStaffJoinWaitlist = vi.mocked(staffJoinWaitlist)
const mockedListClinicDoctors = vi.mocked(listClinicDoctors)

const CLINIC_ID = 'clinic-1'

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

    await user.type(screen.getByLabelText(/patient account/i), 'account-1')
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

    await user.type(screen.getByLabelText(/patient account/i), 'account-1')
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

  // _diagnostics [MAJOR] - [full-repo-audit] - [SILENT_EMPTY_SELECT]
  it('shows a helpful message instead of a silently unfillable select when the clinic has no doctors', async () => {
    mockedListClinicDoctors.mockReset().mockResolvedValue({ doctors: [], page: 0, pageSize: 500, totalCount: 0 })
    const user = userEvent.setup()

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)
    await user.type(screen.getByLabelText(/patient account/i), 'account-1')

    expect(await screen.findByText(/no doctors are staffed at this clinic yet/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/^doctor$/i)).not.toBeInTheDocument()
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedStaffJoinWaitlist.mockRejectedValueOnce(new WaitlistJoinApiError({ error: 'FORBIDDEN' }))

    render(<StaffJoinWaitlistForm clinicId={CLINIC_ID} />)

    await user.type(screen.getByLabelText(/patient account/i), 'account-1')
    await screen.findByRole('option', { name: /dr\. priya nair — dr-1001/i })
    await user.selectOptions(screen.getByLabelText(/^doctor$/i), 'doctor-1')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff/i)
  })
})
