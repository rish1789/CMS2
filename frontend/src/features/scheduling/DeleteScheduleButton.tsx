import { useState } from 'react'
import { deleteSchedule } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ApiError } from '../../lib/apiClient'

export interface DeleteScheduleButtonProps {
  clinicId: string
  doctorProfileId: string
  scheduleId: string
  onDeleted: () => void
}

type Phase = 'idle' | 'confirming' | 'submitting'

// 055-schedule-break-window: lets staff remove a now-redundant Schedule (e.g. after merging two
// side-by-side blocks into one with a break window). Unlike DeleteSessionButton, this never
// dead-ends in a permanent "blocked" state - ScheduleDeletionService detaches any Session with
// real Booking/waitlist history instead of rejecting the whole deletion, so a failure here only
// ever means "try again" (network/auth), not "this can never be deleted."
export function DeleteScheduleButton({ clinicId, doctorProfileId, scheduleId, onDeleted }: DeleteScheduleButtonProps) {
  const [session] = useState(() => loadStaffSession())
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    if (!session) return
    setPhase('submitting')
    setError(null)

    try {
      await deleteSchedule(clinicId, doctorProfileId, scheduleId, session.token)
      onDeleted()
    } catch (err) {
      setPhase('confirming')
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to delete this schedule.</p>
  }

  if (phase === 'confirming' || phase === 'submitting') {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-700">Delete this schedule permanently?</span>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {phase === 'submitting' ? 'Deleting…' : 'Confirm'}
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
    <button
      type="button"
      onClick={() => setPhase('confirming')}
      className="shrink-0 text-sm font-medium text-red-600 transition-colors duration-150 hover:text-red-700"
    >
      Delete
    </button>
  )
}
