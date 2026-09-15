import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CompleteSlotButton } from '../../src/features/session-delay/CompleteSlotButton'
import { completeSlot, SlotCompletionApiError } from '../../src/features/session-delay/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return {
    ...actual,
    completeSlot: vi.fn(),
  }
})

const mockedCompleteSlot = vi.mocked(completeSlot)

const CLINIC_ID = 'clinic-1'
const SLOT_ID = 'slot-1'

describe('CompleteSlotButton', () => {
  beforeEach(() => {
    mockedCompleteSlot.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('marks the slot completed and shows a confirmation', async () => {
    const user = userEvent.setup()
    mockedCompleteSlot.mockResolvedValueOnce({ slotId: SLOT_ID, status: 'COMPLETED' })

    render(<CompleteSlotButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /mark completed/i }))

    expect(await screen.findByText(/slot marked completed/i)).toBeInTheDocument()
    expect(mockedCompleteSlot).toHaveBeenCalledWith(CLINIC_ID, SLOT_ID, 'a.jwt.token')
  })

  it('shows the SLOT_NOT_COMPLETABLE error message', async () => {
    const user = userEvent.setup()
    mockedCompleteSlot.mockRejectedValueOnce(new SlotCompletionApiError({ error: 'SLOT_NOT_COMPLETABLE' }))

    render(<CompleteSlotButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /mark completed/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be marked completed/i)
  })

  it('shows the NOT_A_FIXED_TIME_SESSION error message', async () => {
    const user = userEvent.setup()
    mockedCompleteSlot.mockRejectedValueOnce(new SlotCompletionApiError({ error: 'NOT_A_FIXED_TIME_SESSION' }))

    render(<CompleteSlotButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /mark completed/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/fixed-time sessions/i)
  })
})
