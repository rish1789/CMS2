import { useState } from 'react'
import { cancelSession, SessionCancellationApiError } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface CancelSessionButtonProps {
  clinicId: string
  sessionId: string
  /**
   * Pass `false` when the caller already knows (e.g. from the slot list it just rendered)
   * that there's nothing booked to cancel - skips the confirm step and the wasted API round
   * trip entirely, going straight to the same message a SESSION_ALREADY_CANCELLED response
   * would produce. Omit when the caller doesn't have this information up front.
   */
  hasActiveBookings?: boolean
  onCancelled?: (bookingsCancelled: number) => void
}

type Phase = 'idle' | 'confirming' | 'submitting' | 'done' | 'blocked'

const NOTHING_TO_CANCEL_MESSAGE = 'This session has nothing left to cancel.'

// 042-day-sheet-hardening FR-001/FR-002: a single click must never be sufficient to cancel a
// whole session - same idle/confirming/submitting shape already used elsewhere in this codebase
// (staff deactivation, individual booking cancellation) rather than a new interaction pattern.
export function CancelSessionButton({ clinicId, sessionId, hasActiveBookings, onCancelled }: CancelSessionButtonProps) {
  const [session] = useState(() => loadStaffSession())
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)
  const [bookingsCancelled, setBookingsCancelled] = useState<number | null>(null)

  async function handleConfirm() {
    if (!session) return
    setPhase('submitting')
    setError(null)

    try {
      const response = await cancelSession(clinicId, sessionId, session.token)
      setBookingsCancelled(response.bookingsCancelled)
      setPhase('done')
      onCancelled?.(response.bookingsCancelled)
    } catch (err) {
      // SESSION_ALREADY_CANCELLED can never succeed on a retry (there's nothing left to
      // cancel) - re-showing Confirm/Cancel here would just trap the user in a dead loop, so
      // this one error ends the interaction outright instead of returning to 'confirming'.
      if (err instanceof SessionCancellationApiError && err.body.error === 'SESSION_ALREADY_CANCELLED') {
        setPhase('blocked')
        setError(err.message)
        return
      }
      setPhase('confirming')
      if (err instanceof SessionCancellationApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to cancel this session.</p>
  }

  if (phase === 'done' && bookingsCancelled !== null) {
    return (
      <p className="text-sm text-green-700">
        Session cancelled — {bookingsCancelled} booking{bookingsCancelled === 1 ? '' : 's'} cancelled.
      </p>
    )
  }

  if (phase === 'blocked' && error) {
    return (
      <p role="alert" className="text-sm text-gray-500">
        {error}
      </p>
    )
  }

  if (phase === 'confirming' || phase === 'submitting') {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-700">Are you sure? Active bookings in this session will be cancelled.</span>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {phase === 'submitting' ? 'Cancelling…' : 'Confirm'}
        </button>
        {/* "Back", not "Cancel" - a button labelled "Cancel" inside a cancel-the-session confirm
            dialog reads ambiguously (cancel the dialog, or cancel the session?). Matches
            CancelFromCutoffForm's identical confirm-step button, already labelled "Back". */}
        <button
          type="button"
          onClick={() => {
            setPhase('idle')
            setError(null)
          }}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg border border-gray-300 px-3 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Back
        </button>
        {error && (
          <p role="alert" className="w-full text-sm text-red-700">
            {error}
          </p>
        )}
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={() => {
        if (hasActiveBookings === false) {
          setPhase('blocked')
          setError(NOTHING_TO_CANCEL_MESSAGE)
          return
        }
        setPhase('confirming')
      }}
      className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100"
    >
      <svg aria-hidden="true" width="14" height="14" viewBox="0 0 14 14" fill="none">
        <circle cx="7" cy="7" r="5.5" stroke="currentColor" strokeWidth="1.4" />
        <path d="M4.8 4.8l4.4 4.4M9.2 4.8l-4.4 4.4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
      Cancel entire session
    </button>
  )
}
