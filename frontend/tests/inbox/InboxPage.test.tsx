import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InboxPage } from '../../src/features/inbox/InboxPage'
import { claimItem, listInboxItems, openInboxStream, releaseItem, resolveItem, InboxApiError } from '../../src/features/inbox/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/inbox/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/inbox/api')>('../../src/features/inbox/api')
  return {
    ...actual,
    listInboxItems: vi.fn(),
    claimItem: vi.fn(),
    releaseItem: vi.fn(),
    resolveItem: vi.fn(),
    openInboxStream: vi.fn(),
  }
})

const mockedList = vi.mocked(listInboxItems)
const mockedClaim = vi.mocked(claimItem)
const mockedRelease = vi.mocked(releaseItem)
const mockedResolve = vi.mocked(resolveItem)
const mockedStream = vi.mocked(openInboxStream)

const CLINIC_ID = 'clinic-1'

const walkInItem = {
  id: 'item-1',
  itemType: 'WALK_IN' as const,
  status: 'UNCLAIMED' as const,
  claimedByAccountId: null,
  claimedByName: null,
  createdAt: '2026-09-05T10:00:00Z',
  summary: { bookingId: 'b-1', patientName: 'Jane Doe', slotStartTime: '10:15' },
}

describe('InboxPage', () => {
  let streamCallback: ((item: typeof walkInItem) => void) | null = null

  beforeEach(() => {
    mockedList.mockReset()
    mockedClaim.mockReset()
    mockedRelease.mockReset()
    mockedResolve.mockReset()
    mockedStream.mockReset()
    streamCallback = null
    mockedStream.mockImplementation((_clinicId, _token, onItem) => {
      streamCallback = onItem
      return { close: vi.fn() }
    })
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('renders the initial list of outstanding items', async () => {
    mockedList.mockResolvedValueOnce([walkInItem])

    render(<InboxPage clinicId={CLINIC_ID} />)

    expect(await screen.findByText(/Walk-in: Jane Doe/i)).toBeInTheDocument()
  })

  it('applies a live stream update, adding a new item without a manual refresh', async () => {
    mockedList.mockResolvedValueOnce([])

    render(<InboxPage clinicId={CLINIC_ID} />)

    await screen.findByText(/no outstanding items/i)
    streamCallback?.(walkInItem)

    expect(await screen.findByText(/Walk-in: Jane Doe/i)).toBeInTheDocument()
  })

  it('claims an item and shows it as claimed by the current staff member', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([walkInItem])
    mockedClaim.mockResolvedValueOnce({ ...walkInItem, status: 'CLAIMED', claimedByAccountId: 'acct-1', claimedByName: 'Ops A' })

    render(<InboxPage clinicId={CLINIC_ID} />)
    await screen.findByText(/Walk-in: Jane Doe/i)

    await user.click(screen.getByRole('button', { name: /claim/i }))

    expect(await screen.findByText(/claimed by ops a/i)).toBeInTheDocument()
    expect(mockedClaim).toHaveBeenCalledWith(CLINIC_ID, 'item-1', 'a.jwt.token')
  })

  it('shows an ALREADY_CLAIMED error message when a claim is rejected, and reconciles stale state with a fresh fetch', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([walkInItem])
    mockedClaim.mockRejectedValueOnce(new InboxApiError({ error: 'ALREADY_CLAIMED' }))
    const claimedByOther = { ...walkInItem, status: 'CLAIMED' as const, claimedByAccountId: 'other-acct', claimedByName: 'Other Staff' }
    mockedList.mockResolvedValueOnce([claimedByOther])

    render(<InboxPage clinicId={CLINIC_ID} />)
    await screen.findByText(/Walk-in: Jane Doe/i)

    await user.click(screen.getByRole('button', { name: /claim/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already claimed/i)
    expect(await screen.findByText(/claimed by other staff/i)).toBeInTheDocument()
  })

  it('resolves a claimed item and removes it from the outstanding list', async () => {
    const user = userEvent.setup()
    const claimedItem = { ...walkInItem, status: 'CLAIMED' as const, claimedByAccountId: 'acct-1', claimedByName: 'Ops A' }
    mockedList.mockResolvedValueOnce([claimedItem])
    mockedResolve.mockResolvedValueOnce({ ...claimedItem, status: 'RESOLVED' as const })

    render(<InboxPage clinicId={CLINIC_ID} />)
    await screen.findByText(/claimed by ops a/i)

    await user.click(screen.getByRole('button', { name: /resolve/i }))

    expect(await screen.findByText(/no outstanding items/i)).toBeInTheDocument()
  })

  it('shows a NOT_CLAIMANT error message when a resolve is rejected', async () => {
    const user = userEvent.setup()
    const claimedItem = { ...walkInItem, status: 'CLAIMED' as const, claimedByAccountId: 'acct-1', claimedByName: 'Ops A' }
    mockedList.mockResolvedValueOnce([claimedItem])
    mockedResolve.mockRejectedValueOnce(new InboxApiError({ error: 'NOT_CLAIMANT' }))
    mockedList.mockResolvedValueOnce([claimedItem])

    render(<InboxPage clinicId={CLINIC_ID} />)
    await screen.findByText(/claimed by ops a/i)

    await user.click(screen.getByRole('button', { name: /resolve/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only the person who claimed/i)
  })

  it('releases a claimed item, returning it to unclaimed', async () => {
    const user = userEvent.setup()
    const claimedItem = { ...walkInItem, status: 'CLAIMED' as const, claimedByAccountId: 'acct-1', claimedByName: 'Ops A' }
    mockedList.mockResolvedValueOnce([claimedItem])
    mockedRelease.mockResolvedValueOnce({ ...walkInItem, status: 'UNCLAIMED' })

    render(<InboxPage clinicId={CLINIC_ID} />)
    await screen.findByText(/claimed by ops a/i)

    await user.click(screen.getByRole('button', { name: /release/i }))

    expect(await screen.findByText(/unclaimed/i)).toBeInTheDocument()
  })
})
