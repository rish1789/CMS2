import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppearedButton } from '../../src/features/session-delay/AppearedButton'
import { markSlotAppeared, SlotAppearedApiError } from '../../src/features/session-delay/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return {
    ...actual,
    markSlotAppeared: vi.fn(),
  }
})

const mockedMarkSlotAppeared = vi.mocked(markSlotAppeared)

const CLINIC_ID = 'clinic-1'
const SLOT_ID = 'slot-1'

describe('AppearedButton', () => {
  beforeEach(() => {
    mockedMarkSlotAppeared.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('marks the slot appeared and shows a confirmation', async () => {
    const user = userEvent.setup()
    mockedMarkSlotAppeared.mockResolvedValueOnce({ slotId: SLOT_ID, status: 'APPEARED' })

    render(<AppearedButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /^appeared$/i }))

    expect(await screen.findByText(/slot marked appeared/i)).toBeInTheDocument()
    expect(mockedMarkSlotAppeared).toHaveBeenCalledWith(CLINIC_ID, SLOT_ID, 'a.jwt.token')
  })

  it('calls onAppeared once marking succeeds', async () => {
    const user = userEvent.setup()
    const onAppeared = vi.fn()
    mockedMarkSlotAppeared.mockResolvedValueOnce({ slotId: SLOT_ID, status: 'APPEARED' })

    render(<AppearedButton clinicId={CLINIC_ID} slotId={SLOT_ID} onAppeared={onAppeared} />)

    await user.click(screen.getByRole('button', { name: /^appeared$/i }))

    expect(await screen.findByText(/slot marked appeared/i)).toBeInTheDocument()
    expect(onAppeared).toHaveBeenCalledOnce()
  })

  it('shows the SLOT_NOT_APPEARABLE error message', async () => {
    const user = userEvent.setup()
    mockedMarkSlotAppeared.mockRejectedValueOnce(new SlotAppearedApiError({ error: 'SLOT_NOT_APPEARABLE' }))

    render(<AppearedButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /^appeared$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be marked appeared/i)
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedMarkSlotAppeared.mockRejectedValueOnce(new SlotAppearedApiError({ error: 'FORBIDDEN' }))

    render(<AppearedButton clinicId={CLINIC_ID} slotId={SLOT_ID} />)

    await user.click(screen.getByRole('button', { name: /^appeared$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/ClinicAdmin can mark a slot appeared/i)
  })
})
