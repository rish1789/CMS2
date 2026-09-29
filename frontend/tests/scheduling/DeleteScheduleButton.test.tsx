import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DeleteScheduleButton } from '../../src/features/scheduling/DeleteScheduleButton'
import { deleteSchedule } from '../../src/features/scheduling/api'
import { ApiError } from '../../src/lib/apiClient'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/scheduling/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/scheduling/api')>(
    '../../src/features/scheduling/api',
  )
  return {
    ...actual,
    deleteSchedule: vi.fn(),
  }
})

const mockedDeleteSchedule = vi.mocked(deleteSchedule)

const CLINIC_ID = 'clinic-1'
const DOCTOR_ID = 'doctor-1'
const SCHEDULE_ID = 'sched-1'

describe('DeleteScheduleButton', () => {
  const onDeleted = vi.fn()

  beforeEach(() => {
    mockedDeleteSchedule.mockReset()
    onDeleted.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('requires confirmation before deleting - a single click does not call the API', async () => {
    const user = userEvent.setup()
    render(
      <DeleteScheduleButton clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} scheduleId={SCHEDULE_ID} onDeleted={onDeleted} />,
    )

    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(mockedDeleteSchedule).not.toHaveBeenCalled()
    expect(screen.getByText(/delete this schedule permanently/i)).toBeInTheDocument()
  })

  it('deletes the schedule and calls onDeleted after Confirm', async () => {
    const user = userEvent.setup()
    mockedDeleteSchedule.mockResolvedValueOnce(undefined)

    render(
      <DeleteScheduleButton clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} scheduleId={SCHEDULE_ID} onDeleted={onDeleted} />,
    )

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(mockedDeleteSchedule).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_ID, SCHEDULE_ID, 'a.jwt.token')
    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1))
  })

  it('shows the error and stays on the confirm step so the user can retry', async () => {
    const user = userEvent.setup()
    mockedDeleteSchedule.mockRejectedValueOnce(new ApiError(403, 'Only this clinic’s ClinicAdmin, or the doctor themselves, can manage this schedule.', { error: 'FORBIDDEN' }))

    render(
      <DeleteScheduleButton clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} scheduleId={SCHEDULE_ID} onDeleted={onDeleted} />,
    )

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/clinicadmin/i)
    expect(screen.getByRole('button', { name: /^confirm$/i })).toBeInTheDocument()
    expect(onDeleted).not.toHaveBeenCalled()
  })
})
