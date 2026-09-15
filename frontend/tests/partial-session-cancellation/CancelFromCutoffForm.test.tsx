import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CancelFromCutoffForm } from '../../src/features/partial-session-cancellation/CancelFromCutoffForm'
import {
  cancelFromCutoff,
  PartialCancellationApiError,
} from '../../src/features/partial-session-cancellation/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/partial-session-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/partial-session-cancellation/api')>(
    '../../src/features/partial-session-cancellation/api',
  )
  return {
    ...actual,
    cancelFromCutoff: vi.fn(),
  }
})

const mockedCancelFromCutoff = vi.mocked(cancelFromCutoff)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

async function fillRange(from: string, to: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/cancel slots from/i), from)
  await user.type(screen.getByLabelText(/^to$/i), to)
  await user.click(screen.getByRole('button', { name: /cancel these slots/i }))
}

async function fillRangeAndConfirm(from: string, to: string) {
  await fillRange(from, to)
  await userEvent.setup().click(await screen.findByRole('button', { name: /^confirm$/i }))
}

describe('CancelFromCutoffForm', () => {
  beforeEach(() => {
    mockedCancelFromCutoff.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('requires confirmation before cancelling - submitting the range does not call the API', async () => {
    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRange('14:00', '16:00')

    expect(mockedCancelFromCutoff).not.toHaveBeenCalled()
    expect(screen.getByText(/cancel every booking between/i)).toBeInTheDocument()
    expect(screen.getByText('14:00')).toBeInTheDocument()
    expect(screen.getByText('16:00')).toBeInTheDocument()
  })

  it('clicking Back on the confirmation step returns to the form without calling the API', async () => {
    const user = userEvent.setup()
    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRange('14:00', '16:00')

    await user.click(screen.getByRole('button', { name: /^back$/i }))

    expect(mockedCancelFromCutoff).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /cancel these slots/i })).toBeInTheDocument()
  })

  it('submits a from/to time range and shows the count of bookings cancelled after Confirm', async () => {
    mockedCancelFromCutoff.mockResolvedValueOnce({ sessionId: SESSION_ID, bookingsCancelled: 3 })

    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRangeAndConfirm('14:00', '16:00')

    expect(await screen.findByText(/3 bookings cancelled/i)).toBeInTheDocument()
    expect(mockedCancelFromCutoff).toHaveBeenCalledWith(CLINIC_ID, SESSION_ID, '14:00:00', '16:00:00', 'a.jwt.token')
  })

  it('shows a friendly message when zero bookings qualify', async () => {
    mockedCancelFromCutoff.mockResolvedValueOnce({ sessionId: SESSION_ID, bookingsCancelled: 0 })

    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRangeAndConfirm('22:00', '23:00')

    expect(await screen.findByText(/nothing was scheduled/i)).toBeInTheDocument()
  })

  it('shows the FORBIDDEN error message', async () => {
    mockedCancelFromCutoff.mockRejectedValueOnce(new PartialCancellationApiError({ error: 'FORBIDDEN' }))

    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRangeAndConfirm('14:00', '16:00')

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff/i)
  })

  it('shows the INVALID_CANCELLATION_RANGE error message when "to" is not after "from"', async () => {
    mockedCancelFromCutoff.mockRejectedValueOnce(
      new PartialCancellationApiError({ error: 'INVALID_CANCELLATION_RANGE' }),
    )

    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRangeAndConfirm('16:00', '14:00')

    expect(await screen.findByRole('alert')).toHaveTextContent(/end time must be after the start time/i)
  })

  it('resets and stays usable for a second cancellation after a successful submit, instead of disappearing', async () => {
    mockedCancelFromCutoff.mockResolvedValueOnce({ sessionId: SESSION_ID, bookingsCancelled: 3 })

    render(<CancelFromCutoffForm clinicId={CLINIC_ID} sessionId={SESSION_ID} />)
    await fillRangeAndConfirm('14:00', '16:00')
    expect(await screen.findByText(/3 bookings cancelled/i)).toBeInTheDocument()

    // The fields must be empty again (not still showing 14:00/16:00) and ready for another
    // range - this control must not be a one-shot that vanishes after its first use.
    expect(screen.getByLabelText(/cancel slots from/i)).toHaveValue('')
    expect(screen.getByLabelText(/^to$/i)).toHaveValue('')

    mockedCancelFromCutoff.mockResolvedValueOnce({ sessionId: SESSION_ID, bookingsCancelled: 1 })
    await fillRangeAndConfirm('18:00', '19:00')

    expect(await screen.findByText(/1 booking cancelled/i)).toBeInTheDocument()
    expect(screen.queryByText(/3 bookings cancelled/i)).not.toBeInTheDocument()
    expect(mockedCancelFromCutoff).toHaveBeenLastCalledWith(CLINIC_ID, SESSION_ID, '18:00:00', '19:00:00', 'a.jwt.token')
  })
})
