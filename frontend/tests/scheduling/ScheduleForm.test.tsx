import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ScheduleForm } from '../../src/features/scheduling/ScheduleForm'
import { createSchedule, ScheduleApiError } from '../../src/features/scheduling/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/scheduling/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/scheduling/api')>(
    '../../src/features/scheduling/api',
  )
  return {
    ...actual,
    createSchedule: vi.fn(),
  }
})

const mockedCreateSchedule = vi.mocked(createSchedule)

const CLINIC_ID = 'clinic-1'
const DOCTOR_ID = 'doctor-1'

describe('ScheduleForm', () => {
  beforeEach(() => {
    mockedCreateSchedule.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'admin@example.com' })
  })

  it('submits a valid Fixed-Time schedule and shows the success confirmation', async () => {
    const user = userEvent.setup()
    mockedCreateSchedule.mockResolvedValueOnce({
      id: 'sched-1',
      clinicId: CLINIC_ID,
      doctorProfileId: DOCTOR_ID,
      daysOfWeek: ['MONDAY'],
      startTime: '09:00',
      endTime: '13:00',
      mode: 'FIXED_TIME',
      slotIntervalMinutes: 15,
    })

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/start time/i), '09:00')
    await user.type(screen.getByLabelText(/end time/i), '13:00')
    await user.type(screen.getByLabelText(/slot interval/i), '15')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByText(/schedule created/i)).toBeInTheDocument()
    // staff-console-audit-2026-09-10 P3: the confirmation used to show the raw enum ("MONDAY");
    // both the checkbox label and this summary now read "Monday". The API payload still sends
    // the raw enum, unaffected by the display change.
    expect(screen.getByText(/monday/i)).toHaveTextContent('Monday')
    expect(screen.queryByText('MONDAY')).not.toBeInTheDocument()
    expect(mockedCreateSchedule).toHaveBeenCalledWith(
      CLINIC_ID,
      DOCTOR_ID,
      expect.objectContaining({ daysOfWeek: ['MONDAY'], mode: 'FIXED_TIME', slotIntervalMinutes: 15 }),
      'a.jwt.token',
    )
  })

  it('shows the FORBIDDEN error message returned by the API', async () => {
    const user = userEvent.setup()
    mockedCreateSchedule.mockRejectedValueOnce(new ScheduleApiError({ error: 'FORBIDDEN' }))

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/start time/i), '09:00')
    await user.type(screen.getByLabelText(/end time/i), '13:00')
    await user.type(screen.getByLabelText(/slot interval/i), '15')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/clinicadmin/i)
  })
})
