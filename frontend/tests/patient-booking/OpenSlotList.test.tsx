import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OpenSlotList } from '../../src/features/patient-booking/OpenSlotList'
import { todayIsoDate } from '../../src/features/patient-booking/DateStrip'
import { listOpenSlots } from '../../src/features/patient-booking/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-booking/api')>(
    '../../src/features/patient-booking/api',
  )
  return {
    ...actual,
    listOpenSlots: vi.fn(),
  }
})

const mockedListOpenSlots = vi.mocked(listOpenSlots)

const CLINIC_ID = 'clinic-1'

const ONE_SLOT_RESULT = {
  slots: [
    {
      slotId: 'slot-1',
      doctorProfileId: 'doc-1',
      doctorName: 'Dr. Asha Rao',
      sessionDate: '2026-09-10',
      startTime: '09:00:00',
      endTime: '09:15:00',
      appointmentTypes: [{ id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', feeOverride: null }],
    },
  ],
  page: 0,
  pageSize: 15,
  totalCount: 1,
}

describe('OpenSlotList', () => {
  beforeEach(() => {
    mockedListOpenSlots.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'acct-1', email: 'patient@example.com' })
  })

  it('lists open slots with doctor and time detail, defaulting to today', async () => {
    mockedListOpenSlots.mockResolvedValueOnce(ONE_SLOT_RESULT)

    render(<OpenSlotList clinicId={CLINIC_ID} />)

    expect(await screen.findByText('Dr. Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('09:00')).toBeInTheDocument()
    expect(screen.getByText('15 min')).toBeInTheDocument()
    expect(mockedListOpenSlots).toHaveBeenCalledWith(
      CLINIC_ID,
      'a.jwt.token',
      undefined,
      expect.objectContaining({ page: 0, size: 15, date: todayIsoDate() }),
    )
  })

  it('shows an empty state when there are no open slots on the selected date', async () => {
    mockedListOpenSlots.mockResolvedValueOnce({ slots: [], page: 0, pageSize: 15, totalCount: 0 })

    render(<OpenSlotList clinicId={CLINIC_ID} />)

    expect(await screen.findByText(/no open slots on this date/i)).toBeInTheDocument()
  })

  it('opens a booking form for the selected slot', async () => {
    const user = userEvent.setup()
    mockedListOpenSlots.mockResolvedValueOnce(ONE_SLOT_RESULT)

    render(<OpenSlotList clinicId={CLINIC_ID} />)

    await user.click(await screen.findByRole('button', { name: /book/i }))

    expect(await screen.findByRole('form', { name: /book slot/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/your name/i)).toBeInTheDocument()
  })

  it('re-fetches with the newly selected date when a date-strip pill is clicked', async () => {
    const user = userEvent.setup()
    mockedListOpenSlots.mockResolvedValueOnce(ONE_SLOT_RESULT)

    render(<OpenSlotList clinicId={CLINIC_ID} />)
    await screen.findByText('Dr. Asha Rao')

    mockedListOpenSlots.mockResolvedValueOnce({ slots: [], page: 0, pageSize: 15, totalCount: 0 })
    await user.click(screen.getByRole('button', { name: /tmrw/i }))

    expect(await screen.findByText(/no open slots on this date/i)).toBeInTheDocument()
    const lastCallParams = mockedListOpenSlots.mock.calls.at(-1)?.[3]
    expect(lastCallParams?.date).not.toBe(todayIsoDate())
  })
})
