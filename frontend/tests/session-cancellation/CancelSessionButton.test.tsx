import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CancelSessionButton } from '../../src/features/session-cancellation/CancelSessionButton'
import { cancelSession, SessionCancellationApiError } from '../../src/features/session-cancellation/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/session-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-cancellation/api')>(
    '../../src/features/session-cancellation/api',
  )
  return {
    ...actual,
    cancelSession: vi.fn(),
  }
})

const mockedCancelSession = vi.mocked(cancelSession)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

describe('CancelSessionButton', () => {
  beforeEach(() => {
    mockedCancelSession.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('requires confirmation before cancelling - a single click does not call the API', async () => {
    const user = userEvent.setup()
    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))

    expect(mockedCancelSession).not.toHaveBeenCalled()
    expect(screen.getByText(/active bookings in this session will be cancelled/i)).toBeInTheDocument()
  })

  it('clicking Back on the confirmation step does not call the API', async () => {
    const user = userEvent.setup()
    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))
    // "Back", not "Cancel" - a button labelled "Cancel" inside a cancel-the-session confirm
    // dialog reads ambiguously (cancel the dialog, or cancel the session?).
    await user.click(screen.getByRole('button', { name: /^back$/i }))

    expect(mockedCancelSession).not.toHaveBeenCalled()
    // Back to the initial state - the trigger button is showing again.
    expect(screen.getByRole('button', { name: /cancel entire session/i })).toBeInTheDocument()
  })

  it('cancels the session and shows the count of bookings cancelled after Confirm', async () => {
    const user = userEvent.setup()
    mockedCancelSession.mockResolvedValueOnce({ sessionId: SESSION_ID, bookingsCancelled: 4 })

    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByText(/4 bookings cancelled/i)).toBeInTheDocument()
    expect(mockedCancelSession).toHaveBeenCalledWith(CLINIC_ID, SESSION_ID, 'a.jwt.token')
  })

  it('ends the interaction (no dead-end retry loop) when there is nothing left to cancel', async () => {
    const user = userEvent.setup()
    mockedCancelSession.mockRejectedValueOnce(new SessionCancellationApiError({ error: 'SESSION_ALREADY_CANCELLED' }))

    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/nothing left to cancel/i)
    // Retrying can never succeed here, so Confirm/Back must not still be sitting there.
    expect(screen.queryByRole('button', { name: /^confirm$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^back$/i })).not.toBeInTheDocument()
  })

  it('shows the nothing-to-cancel message immediately on click when the caller already knows there are no active bookings, with no API call and no confirm step', async () => {
    const user = userEvent.setup()
    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} hasActiveBookings={false} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/nothing left to cancel/i)
    expect(mockedCancelSession).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: /^confirm$/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/are you sure/i)).not.toBeInTheDocument()
  })

  it('shows the FORBIDDEN error message after Confirm', async () => {
    const user = userEvent.setup()
    mockedCancelSession.mockRejectedValueOnce(new SessionCancellationApiError({ error: 'FORBIDDEN' }))

    render(<CancelSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel entire session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff/i)
  })
})
