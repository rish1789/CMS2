import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { InboxItemCard } from '../../src/features/inbox/InboxItemCard'
import type { InboxItemResponse } from '../../src/features/inbox/api'

const BASE_ITEM: InboxItemResponse = {
  id: 'item-1',
  itemType: 'DEVERIFICATION_CASCADE',
  status: 'UNCLAIMED',
  claimedByAccountId: null,
  claimedByName: null,
  createdAt: '2026-09-10T10:00:00Z',
  summary: { doctorName: 'Dr. Priya Nair', cancelledBookingCount: 1 },
}

const noop = vi.fn()

// staff-console-audit-2026-09-10 P3: this summary used to read "1 booking(s) cancelled" and
// "3 booking(s) cancelled" alike - the only inbox item type that didn't pluralize properly,
// unlike CancelSessionButton/CancelFromCutoffForm's identical count-then-noun pattern.
describe('InboxItemCard summary text', () => {
  it('uses the singular "booking" for a count of exactly one', () => {
    render(<InboxItemCard item={BASE_ITEM} currentAccountId="acct-1" onClaim={noop} onRelease={noop} onResolve={noop} />)

    expect(screen.getByText(/1 booking cancelled/i)).toBeInTheDocument()
    expect(screen.queryByText(/booking\(s\)/i)).not.toBeInTheDocument()
  })

  it('uses the plural "bookings" for a count greater than one', () => {
    const item = { ...BASE_ITEM, summary: { ...BASE_ITEM.summary, cancelledBookingCount: 3 } }
    render(<InboxItemCard item={item} currentAccountId="acct-1" onClaim={noop} onRelease={noop} onResolve={noop} />)

    expect(screen.getByText(/3 bookings cancelled/i)).toBeInTheDocument()
  })

  it('uses the clinic-level phrasing (no doctor name) when the cascade was not doctor-specific', () => {
    const item = { ...BASE_ITEM, summary: { cancelledBookingCount: 2 } }
    render(<InboxItemCard item={item} currentAccountId="acct-1" onClaim={noop} onRelease={noop} onResolve={noop} />)

    expect(screen.getByText(/clinic de-verified – 2 bookings cancelled/i)).toBeInTheDocument()
  })
})
