import { useState } from 'react'
import { cancelBookingAsPatient, cancelBookingAsStaff, BookingCancellationApiError, type CancellationReason } from './api'
import { loadStaffSession } from '../staff-login/token'
import { loadPatientSession } from '../patient-account/token'

export type CancelBookingButtonProps =
  | { mode: 'staff'; clinicId: string; bookingId: string; onCancelled?: () => void }
  | { mode: 'patient'; bookingId: string; onCancelled?: () => void }

type Phase = 'idle' | 'confirming' | 'submitting' | 'done'

const REASON_OPTIONS: { value: CancellationReason; label: string }[] = [
  { value: 'SCHEDULE_CONFLICT', label: 'Schedule conflict' },
  { value: 'FEELING_BETTER', label: 'Feeling better' },
  { value: 'FOUND_ANOTHER_PROVIDER', label: 'Found another provider' },
  { value: 'PERSONAL_EMERGENCY', label: 'Personal emergency' },
  { value: 'OTHER', label: 'Other' },
]

// staff-console-audit-2026-09-10 P0: a single click used to cancel a real booking outright,
// with no confirmation step at all - the only destructive control in the app that had none.
// Same idle/confirming/submitting shape already established by CancelSessionButton.
//
// patient-cancellation-reason: the patient path additionally requires picking a reason (and
// optionally elaborating on it) before Confirm is even clickable - staff cancellations never
// require one (they already have their own accountability trail via clinic membership), so
// that mode keeps the original plain confirm step unchanged.
export function CancelBookingButton(props: CancelBookingButtonProps) {
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)
  const [reason, setReason] = useState<CancellationReason | ''>('')
  const [reasonDetail, setReasonDetail] = useState('')

  async function handleConfirm() {
    setPhase('submitting')
    setError(null)

    try {
      if (props.mode === 'staff') {
        const session = loadStaffSession()
        if (!session) return
        await cancelBookingAsStaff(props.clinicId, props.bookingId, session.token)
      } else {
        const session = loadPatientSession()
        if (!session || !reason) return
        await cancelBookingAsPatient(props.bookingId, session.token, {
          reason,
          reasonDetail: reasonDetail.trim() || undefined,
        })
      }
      setPhase('done')
      props.onCancelled?.()
    } catch (err) {
      setPhase('confirming')
      if (err instanceof BookingCancellationApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  }

  function backOut() {
    setPhase('idle')
    setError(null)
    setReason('')
    setReasonDetail('')
  }

  if (phase === 'done') {
    return <p className="text-sm text-green-700">Booking cancelled.</p>
  }

  if (phase === 'confirming' || phase === 'submitting') {
    if (props.mode === 'patient') {
      return (
        <div className="max-w-sm space-y-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-sm font-medium text-gray-900">Cancel this booking?</p>

          {error && (
            <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
              {error}
            </p>
          )}

          <div>
            <label htmlFor="cancellationReason" className="block text-sm font-medium text-gray-700">
              Reason for cancelling
            </label>
            <select
              id="cancellationReason"
              required
              value={reason}
              onChange={(e) => setReason(e.target.value as CancellationReason)}
              disabled={phase === 'submitting'}
              className="input mt-1"
            >
              <option value="" disabled>
                Select a reason
              </option>
              {REASON_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="cancellationReasonDetail" className="block text-sm font-medium text-gray-700">
              Anything else? (optional)
            </label>
            <textarea
              id="cancellationReasonDetail"
              rows={2}
              value={reasonDetail}
              onChange={(e) => setReasonDetail(e.target.value)}
              disabled={phase === 'submitting'}
              className="input mt-1"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleConfirm}
              disabled={phase === 'submitting' || !reason}
              className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {phase === 'submitting' ? 'Cancelling…' : 'Confirm cancellation'}
            </button>
            <button
              type="button"
              onClick={backOut}
              disabled={phase === 'submitting'}
              className="inline-flex h-9 items-center rounded-lg border border-gray-300 px-3 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Keep booking
            </button>
          </div>
        </div>
      )
    }

    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-700">Are you sure you want to cancel this booking?</span>
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
          onClick={backOut}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg border border-gray-300 px-3 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Cancel
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
      className="rounded-lg bg-red-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
    >
      Cancel booking
    </button>
  )
}
