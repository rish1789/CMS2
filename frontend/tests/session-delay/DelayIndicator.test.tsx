import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DelayIndicator } from '../../src/features/session-delay/DelayIndicator'
import { getSessionDelay } from '../../src/features/session-delay/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return {
    ...actual,
    getSessionDelay: vi.fn(),
  }
})

const mockedGetSessionDelay = vi.mocked(getSessionDelay)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

describe('DelayIndicator', () => {
  beforeEach(() => {
    mockedGetSessionDelay.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('renders the current delay in minutes when present', async () => {
    mockedGetSessionDelay.mockResolvedValueOnce({ sessionId: SESSION_ID, applicable: true, delayMinutes: 12 })

    render(<DelayIndicator clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByText(/running 12 min behind/i)).toBeInTheDocument()
  })

  it('renders an on-time state when delayMinutes is null', async () => {
    mockedGetSessionDelay.mockResolvedValueOnce({ sessionId: SESSION_ID, applicable: true, delayMinutes: null })

    render(<DelayIndicator clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    expect(await screen.findByText(/on time/i)).toBeInTheDocument()
  })

  it('renders nothing when applicable is false (Queue-mode session)', async () => {
    mockedGetSessionDelay.mockResolvedValueOnce({ sessionId: SESSION_ID, applicable: false, delayMinutes: null })

    render(<DelayIndicator clinicId={CLINIC_ID} sessionId={SESSION_ID} />)

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.queryByText(/on time/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/running/i)).not.toBeInTheDocument()
  })
})
