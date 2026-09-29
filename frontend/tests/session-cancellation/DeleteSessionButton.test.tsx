import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DeleteSessionButton } from '../../src/features/session-cancellation/DeleteSessionButton'
import { deleteSession, SessionDeletionApiError } from '../../src/features/session-cancellation/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/session-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-cancellation/api')>(
    '../../src/features/session-cancellation/api',
  )
  return {
    ...actual,
    deleteSession: vi.fn(),
  }
})

const mockedDeleteSession = vi.mocked(deleteSession)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

// real-bug-fix 2026-09-17: distinct from CancelSessionButton - this deletes the Session/Slot
// rows outright, for a Session generated with wrong values from a since-corrected Schedule.
describe('DeleteSessionButton', () => {
  const onDeleted = vi.fn()

  beforeEach(() => {
    mockedDeleteSession.mockReset()
    onDeleted.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('requires confirmation before deleting - a single click does not call the API', async () => {
    const user = userEvent.setup()
    render(<DeleteSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} onDeleted={onDeleted} />)

    await user.click(screen.getByRole('button', { name: /delete session/i }))

    expect(mockedDeleteSession).not.toHaveBeenCalled()
    expect(screen.getByText(/delete this session permanently/i)).toBeInTheDocument()
  })

  it('clicking Back does not call the API', async () => {
    const user = userEvent.setup()
    render(<DeleteSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} onDeleted={onDeleted} />)

    await user.click(screen.getByRole('button', { name: /delete session/i }))
    await user.click(screen.getByRole('button', { name: /^back$/i }))

    expect(mockedDeleteSession).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /delete session/i })).toBeInTheDocument()
  })

  it('deletes the session and calls onDeleted after Confirm', async () => {
    const user = userEvent.setup()
    mockedDeleteSession.mockResolvedValueOnce(undefined)

    render(<DeleteSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} onDeleted={onDeleted} />)

    await user.click(screen.getByRole('button', { name: /delete session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(mockedDeleteSession).toHaveBeenCalledWith(CLINIC_ID, SESSION_ID, 'a.jwt.token')
    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1))
  })

  it('ends the interaction (no dead-end retry loop) when the session has bookings, waitlist offers or a cancellation on record', async () => {
    const user = userEvent.setup()
    mockedDeleteSession.mockRejectedValueOnce(new SessionDeletionApiError({ error: 'SESSION_DELETION_BLOCKED' }))

    render(<DeleteSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} onDeleted={onDeleted} />)

    await user.click(screen.getByRole('button', { name: /delete session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/a cancellation on record/i)
    expect(screen.queryByRole('button', { name: /^confirm$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^back$/i })).not.toBeInTheDocument()
    expect(onDeleted).not.toHaveBeenCalled()
  })

  it('shows the FORBIDDEN error message after Confirm, staying on the confirm step', async () => {
    const user = userEvent.setup()
    mockedDeleteSession.mockRejectedValueOnce(new SessionDeletionApiError({ error: 'FORBIDDEN' }))

    render(<DeleteSessionButton clinicId={CLINIC_ID} sessionId={SESSION_ID} onDeleted={onDeleted} />)

    await user.click(screen.getByRole('button', { name: /delete session/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff/i)
    expect(screen.getByRole('button', { name: /^confirm$/i })).toBeInTheDocument()
  })
})
