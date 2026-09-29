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
      return `Walk-in: ${String(s.patientName ?? 'Unknown patient')} with ${String(s.doctorName ?? 'Unknown doctor')}`
    case 'WAITLIST_OFFER':
      return `Waitlist offer: ${String(s.patientContact ?? 'Unknown contact')}`
    case 'DEVERIFICATION_CASCADE':
      return s.doctorName
        ? `${String(s.doctorName)}'s license revoked – ${String(s.cancelledBookingCount)} ${pluralizeBookings(s.cancelledBookingCount)} cancelled`
        : `Clinic de-verified – ${String(s.cancelledBookingCount)} ${pluralizeBookings(s.cancelledBookingCount)} cancelled`
  }
}

// real-bug-fix 2026-09-16: a walk-in's doctor is already fixed by the time this card exists
// (WalkInInsertionService derives it from the Session, never from staff input) - "Claim"/
// "Resolve" here are unrelated front-desk task-coordination actions (038's own claim-based
// work-item model), not a re-assignment step. Surfaced only for WALK_IN since that's the
// item type staff have read as "assigning the patient" rather than "taking the task".
function actionHint(item: InboxItemResponse): string | null {
  if (item.itemType !== 'WALK_IN') return null
  return item.status === 'UNCLAIMED'
    ? 'Claiming this task assigns it to you for follow-up — the patient is already booked with the doctor shown above.'
    : 'Resolve once the patient has been seated.'
}

// real-bug-fix 2026-09-17: Resolve (front-desk task coordination) and marking the actual visit
// complete (SlotCompletionService, from the Day Sheet's "Mark complete") are two independent
// actions - deliberately NOT merged, since a walk-in that no-shows or gets cancelled never
// reaches COMPLETED and Resolve still needs to work for that case. This is purely informational,
// so staff can see the two haven't silently drifted apart, without either action driving the
// other.
function visitStatusText(item: InboxItemResponse): string | null {
  if (item.itemType !== 'WALK_IN') return null
  switch (item.summary.slotStatus) {
    case 'COMPLETED':
      return 'Visit: completed'
    case 'NO_SHOW':
      return 'Visit: marked no-show'
    case 'BOOKED':
      return 'Visit: not yet completed'
    default:
      return null
  }
}

export function InboxItemCard({ item, currentAccountId, onClaim, onRelease, onResolve }: InboxItemCardProps) {
  const isMine = item.claimedByAccountId === currentAccountId
  const hint = actionHint(item)
  const visitStatus = visitStatusText(item)

  return (
    <li className="rounded-lg border border-gray-200 p-4 shadow-sm">
      <p className="text-sm font-medium text-gray-900">{summaryText(item)}</p>
      <p className="mt-1 text-sm text-gray-600">
        {item.status === 'UNCLAIMED' && 'Unclaimed'}
        {item.status === 'CLAIMED' && `Claimed by ${item.claimedByName ?? item.claimedByAccountId}`}
        {visitStatus && ` · ${visitStatus}`}
      </p>
      {hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
      <div className="mt-3 flex gap-2">
        {item.status === 'UNCLAIMED' && (
          <button
            type="button"
            onClick={() => onClaim(item.id)}
            className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            Claim task
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
