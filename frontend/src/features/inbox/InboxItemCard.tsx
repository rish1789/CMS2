import type { InboxItemResponse } from './api'

export interface InboxItemCardProps {
  item: InboxItemResponse
  currentAccountId: string
  onClaim: (itemId: string) => void
  onRelease: (itemId: string) => void
  onResolve: (itemId: string) => void
}

// staff-console-audit-2026-09-10 P3: "booking(s)" read as unfinished next to the proper
// singular/plural handling used everywhere else this app reports a cancellation count
// (CancelSessionButton, CancelFromCutoffForm).
function pluralizeBookings(count: unknown): string {
  return `booking${Number(count) === 1 ? '' : 's'}`
}

function summaryText(item: InboxItemResponse): string {
  const s = item.summary
  switch (item.itemType) {
    case 'WALK_IN':
      return `Walk-in: ${String(s.patientName ?? 'Unknown patient')}`
    case 'WAITLIST_OFFER':
      return `Waitlist offer: ${String(s.patientContact ?? 'Unknown contact')}`
    case 'DEVERIFICATION_CASCADE':
      return s.doctorName
        ? `${String(s.doctorName)}'s license revoked – ${String(s.cancelledBookingCount)} ${pluralizeBookings(s.cancelledBookingCount)} cancelled`
        : `Clinic de-verified – ${String(s.cancelledBookingCount)} ${pluralizeBookings(s.cancelledBookingCount)} cancelled`
  }
}

export function InboxItemCard({ item, currentAccountId, onClaim, onRelease, onResolve }: InboxItemCardProps) {
  const isMine = item.claimedByAccountId === currentAccountId

  return (
    <li className="rounded-lg border border-gray-200 p-4 shadow-sm">
      <p className="text-sm font-medium text-gray-900">{summaryText(item)}</p>
      <p className="mt-1 text-sm text-gray-600">
        {item.status === 'UNCLAIMED' && 'Unclaimed'}
        {item.status === 'CLAIMED' && `Claimed by ${item.claimedByName ?? item.claimedByAccountId}`}
      </p>
      <div className="mt-3 flex gap-2">
        {item.status === 'UNCLAIMED' && (
          <button
            type="button"
            onClick={() => onClaim(item.id)}
            className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            Claim
          </button>
        )}
        {item.status === 'CLAIMED' && isMine && (
          <>
            <button
              type="button"
              onClick={() => onRelease(item.id)}
              className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
            >
              Release
            </button>
            <button
              type="button"
              onClick={() => onResolve(item.id)}
              className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              Resolve
            </button>
          </>
        )}
      </div>
    </li>
  )
}
