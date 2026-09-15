import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyWaitlistEntries, type WaitlistEntryResponse, type WaitlistClaimResponse } from './api'
import { ClaimOfferCard } from './ClaimOfferCard'
import { loadPatientSession } from '../patient-account/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { IconBadge } from '../../components/adminIcons'
import { ClockIcon } from '../../components/staffIcons'

function statusBadgeClass(status: WaitlistEntryResponse['status']): string {
  switch (status) {
    case 'OFFERED':
      return 'bg-indigo-50 text-indigo-700'
    case 'CLAIMED':
      return 'bg-green-50 text-green-700'
    case 'EXPIRED':
      return 'bg-gray-100 text-gray-600'
    default:
      return 'bg-amber-50 text-amber-700'
  }
}

function statusLabel(status: WaitlistEntryResponse['status']): string {
  switch (status) {
    case 'WAITING':
      return 'Waiting'
    case 'OFFERED':
      return 'Offer available'
    case 'CLAIMED':
      return 'Claimed'
    case 'EXPIRED':
      return 'Expired'
  }
}

// _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]: previously no patient had any way to
// obtain the entryId ClaimOfferCard requires - this lists the patient's own entries and renders
// a real, working ClaimOfferCard per OFFERED one.
// patient-bookings-view: styled to match MyBookings' conventions (skeleton loading, styled
// empty/error states, icon-badge cards) - the two now share one tabbed page.
export function MyWaitlistEntries() {
  const [session] = useState(() => loadPatientSession())
  const [entries, setEntries] = useState<WaitlistEntryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // _diagnostics [CRITICAL] - [full-repo-audit] - [UNMOUNTED_CONFIRMATION]: entries
  // claimed/declined this visit keep rendering ClaimOfferCard (which shows its own
  // confirmation from its own local state) even after a background refresh reports the
  // entry has already moved past OFFERED server-side - otherwise the very confirmation of
  // what was just booked/charged never has a chance to be seen (see loadSilently below for
  // the other half of this fix - never blanking the list mid-visit).
  const [resolvedEntryIds, setResolvedEntryIds] = useState<Set<string>>(new Set())

  function loadSilently() {
    if (!session) return
    // Deliberately does NOT setEntries(null) first - that would swap the whole list for a
    // loading skeleton on every refresh, unmounting any ClaimOfferCard mid-confirmation. Only
    // the initial mount (below) needs the skeleton; a refresh-after-action should update the
    // list in place, which React does without unmounting unchanged <li key={entry.id}> rows.
    listMyWaitlistEntries(session.token)
      .then(setEntries)
      .catch(() => setError('Could not load your waitlist entries.'))
  }

  useEffect(() => {
    setEntries(null)
    loadSilently()
  }, [session])

  if (!session) {
    return <p className="text-sm text-gray-600">Sign in to view your waitlist entries.</p>
  }

  function handleClaimed(entryId: string, _booking: WaitlistClaimResponse) {
    setResolvedEntryIds((prev) => new Set(prev).add(entryId))
    loadSilently()
  }

  function handleDeclined(entryId: string) {
    setResolvedEntryIds((prev) => new Set(prev).add(entryId))
    loadSilently()
  }

  return (
    <div className="space-y-4">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {entries === null && !error && <ListSkeleton rows={2} />}

      {entries && entries.length === 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          <p>You are not on any waitlist right now.</p>
          <Link
            to="/discover"
            className="mt-2 inline-block font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline"
          >
            Find a doctor
          </Link>
        </div>
      )}

      {entries && entries.length > 0 && (
        <ul className="space-y-3">
          {entries.map((entry) => (
            <li key={entry.id} className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
              <div className="flex items-center gap-3">
                <IconBadge>
                  <ClockIcon />
                </IconBadge>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-gray-900">
                    {entry.specialization ? entry.specialization : 'Requested doctor'}
                  </p>
                  <p className="text-sm text-gray-600">Joined {new Date(entry.joinedAt).toLocaleDateString()}</p>
                </div>
                <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${statusBadgeClass(entry.status)}`}>
                  {statusLabel(entry.status)}
                </span>
              </div>
              {(entry.status === 'OFFERED' || resolvedEntryIds.has(entry.id)) && (
                <div className="mt-3 border-t border-gray-100 pt-3">
                  <ClaimOfferCard
                    entryId={entry.id}
                    offerExpiresAt={entry.offerExpiresAt}
                    offeredDoctorProfileId={entry.offeredDoctorProfileId}
                    onClaimed={(booking) => handleClaimed(entry.id, booking)}
                    onDeclined={() => handleDeclined(entry.id)}
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
