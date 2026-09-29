import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ScheduleForm } from '../../src/features/scheduling/ScheduleForm'
import { createSchedule } from '../../src/features/scheduling/api'
import { ApiError } from '../../src/lib/apiClient'
import { storeStaffSession } from '../../src/features/staff-login/token'

// 065-phase1-stabilization (BUG-006): delay: null keeps every keystroke's events (onChange, inline
// validation) but drops user-event's per-keystroke setTimeout(0) yield. With ~60 typed characters
// per test those yields queue behind other workers' tasks under full-suite parallel load, which
// pushed these tests past the 5 s default timeout intermittently.
const TYPING_OPTIONS = { delay: null }

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
    const user = userEvent.setup(TYPING_OPTIONS)
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
    const user = userEvent.setup(TYPING_OPTIONS)
    // 054-forms-validation-consistency: constructs the shared ApiError directly (matching
    // waitlist/patient-booking's own already-migrated test convention) - the real backend
    // message, not a client-side re-derivation.
    mockedCreateSchedule.mockRejectedValueOnce(
      new ApiError(
        403,
        'Only this clinic’s ClinicAdmin, or the doctor themselves, can manage this schedule.',
        { error: 'FORBIDDEN' },
      ),
    )

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/start time/i), '09:00')
    await user.type(screen.getByLabelText(/end time/i), '13:00')
    await user.type(screen.getByLabelText(/slot interval/i), '15')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/clinicadmin/i)
  })

  // 054-forms-validation-consistency T014: client-side pre-submit checks (research.md
  // Decision 3) block the network call entirely - no ApiError round-trip needed to see them.
  it('blocks submission and shows an inline error when no day of week is selected', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.type(screen.getByLabelText(/start time/i), '09:00')
    await user.type(screen.getByLabelText(/end time/i), '13:00')
    await user.type(screen.getByLabelText(/slot interval/i), '15')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least one day of the week is required/i)
    expect(mockedCreateSchedule).not.toHaveBeenCalled()
    // Other fields' values survive the failed validation attempt.
    expect(screen.getByLabelText(/start time/i)).toHaveValue('09:00')
    expect(screen.getByLabelText(/end time/i)).toHaveValue('13:00')
    expect(screen.getByLabelText(/slot interval/i)).toHaveValue(15)
  })

  it('submits a break window and includes it in the payload', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)
    mockedCreateSchedule.mockResolvedValueOnce({
      id: 'sched-1',
      clinicId: CLINIC_ID,
      doctorProfileId: DOCTOR_ID,
      daysOfWeek: ['MONDAY'],
      startTime: '09:00',
      endTime: '18:00',
      mode: 'FIXED_TIME',
      slotIntervalMinutes: 30,
      breakStartTime: '14:00',
      breakEndTime: '16:00',
    })

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/^start time$/i), '09:00')
    await user.type(screen.getByLabelText(/^end time$/i), '18:00')
    await user.type(screen.getByLabelText(/slot interval/i), '30')
    await user.click(screen.getByLabelText(/add a break/i))
    await user.type(screen.getByLabelText(/break start/i), '14:00')
    await user.type(screen.getByLabelText(/break end/i), '16:00')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByText(/schedule created/i)).toBeInTheDocument()
    expect(screen.getByText(/break 14:00–16:00/i)).toBeInTheDocument()
    expect(mockedCreateSchedule).toHaveBeenCalledWith(
      CLINIC_ID,
      DOCTOR_ID,
      expect.objectContaining({ breakStartTime: '14:00', breakEndTime: '16:00' }),
      'a.jwt.token',
    )
  })

  it('blocks submission and shows an inline error when break end is not after break start', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/^start time$/i), '09:00')
    await user.type(screen.getByLabelText(/^end time$/i), '18:00')
    await user.type(screen.getByLabelText(/slot interval/i), '30')
    await user.click(screen.getByLabelText(/add a break/i))
    await user.type(screen.getByLabelText(/break start/i), '16:00')
    await user.type(screen.getByLabelText(/break end/i), '14:00')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/breakStartTime must be strictly before breakEndTime/i)
    expect(mockedCreateSchedule).not.toHaveBeenCalled()
  })

  it('blocks submission and shows an inline error when start time is not before end time', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)

    render(<ScheduleForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByLabelText('Monday'))
    await user.type(screen.getByLabelText(/start time/i), '13:00')
    await user.type(screen.getByLabelText(/end time/i), '09:00')
    await user.type(screen.getByLabelText(/slot interval/i), '15')
    await user.click(screen.getByRole('button', { name: /save schedule/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/startTime must be strictly before endTime/i)
    expect(mockedCreateSchedule).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Monday')).toBeChecked()
  })
})
