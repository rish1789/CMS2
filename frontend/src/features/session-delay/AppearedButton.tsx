import { useState } from 'react'
import { markSlotAppeared, SlotAppearedApiError } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'

export interface AppearedButtonProps {
  clinicId: string
  slotId: string
  onAppeared?: () => void
}

// 057-day-sheet-status-overhaul: mirrors CompleteSlotButton's shape exactly - the correction
// path (a slot already NO_SHOW) uses this exact same action, so there is no separate button.
export function AppearedButton({ clinicId, slotId, onAppeared }: AppearedButtonProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [appeared, setAppeared] = useState(false)

  async function handleClick() {
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      await markSlotAppeared(clinicId, slotId, session.token)
      setAppeared(true)
      onAppeared?.()
    } catch (err) {
      if (err instanceof SlotAppearedApiError) {
        if (err.body.error === 'UNAUTHORIZED') {
          storeStaffSession(null)
          setSession(null)
        }
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to mark this slot appeared.</p>
  }

  if (appeared) {
    return <p className="text-sm text-green-700">Slot marked appeared.</p>
  }

  return (
    <div className="space-y-2">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}
      <button
        type="button"
        onClick={handleClick}
        disabled={submitting}
        className="rounded-lg bg-cobalt-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-cobalt-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cobalt-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Marking…' : 'Appeared'}
      </button>
    </div>
  )
}
