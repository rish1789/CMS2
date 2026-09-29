import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DoctorScheduleManager } from '../../src/features/scheduling/DoctorScheduleManager'
import { listSchedules, editSchedule, deleteSchedule } from '../../src/features/scheduling/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/scheduling/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/scheduling/api')>(
    '../../src/features/scheduling/api',
  )
  return {
    ...actual,
    listSchedules: vi.fn(),
    editSchedule: vi.fn(),
    deleteSchedule: vi.fn(),
  }
})

const mockedListSchedules = vi.mocked(listSchedules)
const mockedEditSchedule = vi.mocked(editSchedule)
const mockedDeleteSchedule = vi.mocked(deleteSchedule)

const CLINIC_ID = 'clinic-1'
const DOCTOR_ID = 'doctor-1'

const pmSchedule = {
  id: 'sched-pm',
  clinicId: CLINIC_ID,
  doctorProfileId: DOCTOR_ID,
  daysOfWeek: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
  startTime: '16:00:00',
  endTime: '20:00:00',
  mode: 'FIXED_TIME' as const,
  slotIntervalMinutes: 30,
}

// real-bug-fix 2026-09-17: covers the fix for "the schedule form always shows blank, even when
// a schedule already exists" - the actual gap was that no page ever fetched/passed one in.
describe('DoctorScheduleManager', () => {
  beforeEach(() => {
    mockedListSchedules.mockReset()
    mockedEditSchedule.mockReset()
    mockedDeleteSchedule.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'admin@example.com' })
  })

  it('lists existing schedules and lets staff correct a wrong end time', async () => {
    const user = userEvent.setup()
    mockedListSchedules.mockResolvedValueOnce([pmSchedule])

    render(<DoctorScheduleManager clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await screen.findByText(/16:00–20:00/)
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const editForm = within(screen.getByRole('form', { name: /define recurring schedule/i }))
    expect(editForm.getByLabelText(/end time/i)).toHaveValue('20:00')

    mockedEditSchedule.mockResolvedValueOnce({ ...pmSchedule, endTime: '18:00:00' })
    mockedListSchedules.mockResolvedValueOnce([{ ...pmSchedule, endTime: '18:00:00' }])

    await user.clear(editForm.getByLabelText(/end time/i))
    await user.type(editForm.getByLabelText(/end time/i), '18:00')
    await user.click(editForm.getByRole('button', { name: /save changes/i }))

    expect(mockedEditSchedule).toHaveBeenCalledWith(
      CLINIC_ID,
      DOCTOR_ID,
      'sched-pm',
      expect.objectContaining({ endTime: '18:00' }),
      'a.jwt.token',
    )
    expect(await screen.findByText('Schedule updated.')).toBeInTheDocument()
    expect(await screen.findByText(/16:00–18:00/)).toBeInTheDocument()
  })

  it('cancels out of edit mode without saving', async () => {
    const user = userEvent.setup()
    mockedListSchedules.mockResolvedValueOnce([pmSchedule])

    render(<DoctorScheduleManager clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await screen.findByText(/16:00–20:00/)
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    await screen.findByRole('form', { name: /define recurring schedule/i })
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))

    expect(screen.queryByRole('form', { name: /define recurring schedule/i })).not.toBeInTheDocument()
    expect(mockedEditSchedule).not.toHaveBeenCalled()
  })

  it('shows a create form to add another schedule alongside existing ones', async () => {
    const user = userEvent.setup()
    mockedListSchedules.mockResolvedValueOnce([pmSchedule])

    render(<DoctorScheduleManager clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await screen.findByText(/16:00–20:00/)
    await user.click(screen.getByRole('button', { name: /add another schedule/i }))

    expect(screen.getByRole('form', { name: /define recurring schedule/i })).toBeInTheDocument()
  })

  it('deletes a schedule after confirming, and shows the break window when set', async () => {
    const user = userEvent.setup()
    mockedListSchedules.mockResolvedValueOnce([
      { ...pmSchedule, breakStartTime: '14:00:00', breakEndTime: '16:00:00' },
    ])
    mockedDeleteSchedule.mockResolvedValueOnce(undefined)

    render(<DoctorScheduleManager clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    expect(await screen.findByText(/break 14:00–16:00/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    mockedListSchedules.mockResolvedValueOnce([])
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(mockedDeleteSchedule).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_ID, 'sched-pm', 'a.jwt.token')
    expect(await screen.findByText('Schedule deleted.')).toBeInTheDocument()
  })

  it('backing out of a delete confirmation does not call the API', async () => {
    const user = userEvent.setup()
    mockedListSchedules.mockResolvedValueOnce([pmSchedule])

    render(<DoctorScheduleManager clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await screen.findByText(/16:00–20:00/)
    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await user.click(screen.getByRole('button', { name: /^back$/i }))

    expect(mockedDeleteSchedule).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument()
  })
})
