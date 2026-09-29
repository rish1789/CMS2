import { useState } from 'react'
import { cancelBookingsBatch, BatchCancelApiError, type BatchCancelResponse } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface BatchCancelBarProps {
  clinicId: string
  sessionId: string
  selectedBookingIds: string[]
  onCancelled: (result: BatchCancelResponse) => void
  onClear: () => void
}

// 057-day-sheet-status-overhaul US3: appears once at least one Booked/Appeared slot is
// selected via SessionSlotsView's checkboxes; "select all" is handled by the parent (it just
// checks every eligible box) - this bar only ever sees the resulting id list and applies the
// existing per-booking cancellation rule to each one (FR-012), never a separate bulk rule.
export function BatchCancelBar({ clinicId, sessionId, selectedBookingIds, onCancelled, onClear }: BatchCancelBarProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (selectedBookingIds.length === 0) return null

  async function handleCancel() {
    const session = loadStaffSession()
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      const result = await cancelBookingsBatch(clinicId, sessionId, selectedBookingIds, session.token)
      onCancelled(result)
    } catch (err) {
      setError(err instanceof BatchCancelApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-2.5">
      <div className="flex items-center gap-3">
        <span className="text-sm font-medium text-red-800">
          {selectedBookingIds.length} slot{selectedBookingIds.length === 1 ? '' : 's'} selected
        </span>
        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onClear}
          className="rounded-lg px-3 py-1.5 text-sm font-medium text-gray-600 transition-colors duration-150 hover:bg-white"
        >
          Clear
        </button>
        <button
          type="button"
          onClick={handleCancel}
          disabled={submitting}
          className="rounded-lg bg-red-600 px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Cancelling…' : `Cancel ${selectedBookingIds.length === 1 ? 'slot' : 'slots'}`}
        </button>
      </div>
    </div>
  )
}
