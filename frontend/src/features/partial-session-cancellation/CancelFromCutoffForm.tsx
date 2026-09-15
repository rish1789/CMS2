import { useState, type FormEvent } from 'react'
import { cancelFromCutoff, PartialCancellationApiError } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface CancelFromCutoffFormProps {
  clinicId: string
  sessionId: string
  onCancelled?: (bookingsCancelled: number) => void
}

type Phase = 'idle' | 'confirming' | 'submitting'

const timeInputClass =
  'h-9 w-[6.5rem] rounded-lg border border-gray-300 px-2 text-sm text-gray-700 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 [&::-webkit-calendar-picker-indicator]:h-3.5 [&::-webkit-calendar-picker-indicator]:w-3.5 [&::-webkit-calendar-picker-indicator]:opacity-40'

// 042-day-sheet-hardening follow-up: a bounded from/to window (not just "everything from a
// cutoff onward") so staff can clear a single mid-session block - rendered inline in the
// session actions row rather than behind a separate page navigation. Stays mounted and usable
// after a successful cancellation (fields reset, result shown alongside) rather than being
// permanently replaced by a static result message - a receptionist may need to cancel a second,
// different range later in the same session without reloading the page.
//
// staff-console-audit-2026-09-10 P0: submitting the range used to cancel every matching
// booking immediately, with a button literally labelled "Cancel" - the universal abort word -
// sitting next to "Cancel entire session," which does confirm. Now states the blast radius
// and requires an explicit Confirm, same idle/confirming/submitting shape as CancelSessionButton.
export function CancelFromCutoffForm({ clinicId, sessionId, onCancelled }: CancelFromCutoffFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [fromTime, setFromTime] = useState('')
  const [toTime, setToTime] = useState('')
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)
  const [bookingsCancelled, setBookingsCancelled] = useState<number | null>(null)

  function handleReviewRange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setPhase('confirming')
  }

  async function handleConfirm() {
    if (!session) return
    setPhase('submitting')
    setError(null)

    try {
      const response = await cancelFromCutoff(clinicId, sessionId, `${fromTime}:00`, `${toTime}:00`, session.token)
      setBookingsCancelled(response.bookingsCancelled)
      // Reset for the next range instead of leaving stale times behind - this control stays
      // usable for a second cancellation later in the same session (see the class doc comment).
      setFromTime('')
      setToTime('')
      setPhase('idle')
      onCancelled?.(response.bookingsCancelled)
    } catch (err) {
      setPhase('confirming')
      if (err instanceof PartialCancellationApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to cancel part of this session.</p>
  }

  if (phase === 'confirming' || phase === 'submitting') {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-700">
          Cancel every booking between <span className="font-medium text-gray-900">{fromTime}</span> and{' '}
          <span className="font-medium text-gray-900">{toTime}</span>?
        </span>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {phase === 'submitting' ? 'Cancelling…' : 'Confirm'}
        </button>
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
    <form onSubmit={handleReviewRange} className="flex flex-wrap items-center gap-1.5" aria-label="Cancel slots in a time range">
      {/* staff-console-audit-2026-09-10 P3: label+input pairs are grouped so flex-wrap can only
          break BETWEEN them, not between a label and its own field - at 375px this used to wrap
          right after "to", orphaning the label from the input it describes. */}
      <div className="flex items-center gap-1.5">
        <label htmlFor="cutoffFromTime" className="text-sm text-gray-500">
          Cancel slots from
        </label>
        <input
          id="cutoffFromTime"
          type="time"
          required
          value={fromTime}
          onChange={(e) => {
            setFromTime(e.target.value)
            setBookingsCancelled(null)
          }}
          className={timeInputClass}
        />
      </div>
      <div className="flex items-center gap-1.5">
        <label htmlFor="cutoffToTime" className="text-sm text-gray-500">
          to
        </label>
        <input
          id="cutoffToTime"
          type="time"
          required
          value={toTime}
          onChange={(e) => {
            setToTime(e.target.value)
            setBookingsCancelled(null)
          }}
          className={timeInputClass}
        />
      </div>
      {/* staff-console-redesign-2026-09-10: solid, not outline - once staff have already typed a
          range, this is the confident "commit" action; Cancel Entire Session (rarer and more
          severe) stays outline instead, so its lighter weight doesn't invite an accidental click. */}
      <button
        type="submit"
        className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
      >
        Cancel these slots
      </button>
      {/* A transient confirmation next to the (still-usable) form, not a permanent replacement
          of it - a receptionist can cancel a second range later without reloading the page. */}
      {bookingsCancelled !== null && (
        <p className="w-full text-sm text-green-700">
          {bookingsCancelled === 0
            ? 'Nothing was scheduled in that window.'
            : `${bookingsCancelled} booking${bookingsCancelled === 1 ? '' : 's'} cancelled.`}
        </p>
      )}
    </form>
  )
}
