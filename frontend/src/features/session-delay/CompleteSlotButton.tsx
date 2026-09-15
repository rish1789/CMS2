import { useState } from 'react'
import { completeSlot, SlotCompletionApiError } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'

export interface CompleteSlotButtonProps {
  clinicId: string
  slotId: string
  onCompleted?: () => void
}

export function CompleteSlotButton({ clinicId, slotId, onCompleted }: CompleteSlotButtonProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [completed, setCompleted] = useState(false)

  async function handleClick() {
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      await completeSlot(clinicId, slotId, session.token)
      setCompleted(true)
      onCompleted?.()
    } catch (err) {
      if (err instanceof SlotCompletionApiError) {
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
    return <p className="text-sm text-gray-600">Please sign in as staff to mark this slot completed.</p>
  }

  if (completed) {
    return <p className="text-sm text-green-700">Slot marked completed.</p>
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
        className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Marking completed…' : 'Mark completed'}
      </button>
    </div>
  )
}
