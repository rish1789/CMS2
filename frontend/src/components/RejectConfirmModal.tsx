import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { REJECTION_REASON_OPTIONS, type RejectionReason } from './rejectionReason'

export interface RejectConfirmModalProps {
  /** One row for a single-item reject, several for a bulk reject - the modal's copy adapts to the count. */
  items: { id: string; label: string }[]
  /** e.g. "clinic" / "doctor" - used in the modal's copy ("Reject 3 doctors?"). */
  entityNoun: string
  onClose: () => void
  onSubmit: (reasonCode: RejectionReason, detail: string) => Promise<void>
}

// super-admin-console-redesign-2026-09-11: shared by the Clinic and Doctor verification queues'
// single-row and bulk-selection Reject actions - a native <dialog> (showModal()) gives us the
// same focus-trap/Esc-to-close shape as EmployeeModal (staff deactivation) for free, extended
// with a list of exactly what's affected and an optional free-text detail alongside the required
// structured reason.
export function RejectConfirmModal({ items, entityNoun, onClose, onSubmit }: RejectConfirmModalProps) {
  const [reason, setReason] = useState<RejectionReason | ''>('')
  const [detail, setDetail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
  }, [])

  function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current && !submitting) {
      dialogRef.current?.close()
    }
  }

  async function handleSubmit() {
    if (!reason) return
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit(reason, detail.trim())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const count = items.length
  const title = count === 1 ? `Reject ${items[0].label}?` : `Reject ${count} ${entityNoun}s?`

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={handleBackdropClick}
      aria-label={title}
      className="m-auto w-full max-w-md overflow-hidden rounded-xl border-0 bg-white p-0 shadow-xl backdrop:bg-gray-900/50"
    >
      <div className="max-h-[80vh] space-y-4 overflow-y-auto p-5">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            disabled={submitting}
            aria-label="Close"
            className="rounded-md p-1 text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-600 disabled:pointer-events-none disabled:opacity-50"
          >
            <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="currentColor">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </div>

        <p className="text-sm text-gray-600">
          This moves {count === 1 ? 'it' : 'them'} out of the Pending queue into Rejected. It's reversible from the
          Rejected tab.
        </p>

        {count > 1 && (
          <ul className="max-h-32 space-y-1 overflow-y-auto rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700">
            {items.map((item) => (
              <li key={item.id}>{item.label}</li>
            ))}
          </ul>
        )}

        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}

        <div>
          <label htmlFor="reject-reason" className="block text-sm font-medium text-gray-700">
            Reason
          </label>
          <select
            id="reject-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value as RejectionReason)}
            disabled={submitting}
            className="input mt-1"
          >
            <option value="">Select a reason…</option>
            {REJECTION_REASON_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="reject-detail" className="block text-sm font-medium text-gray-700">
            Detail <span className="font-normal text-gray-400">(optional)</span>
          </label>
          <textarea
            id="reject-detail"
            value={detail}
            onChange={(event) => setDetail(event.target.value)}
            disabled={submitting}
            rows={2}
            className="input mt-1"
          />
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            disabled={submitting}
            className="rounded-lg border border-gray-300 px-3.5 py-2 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:opacity-50 disabled:pointer-events-none"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!reason || submitting}
            className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {submitting ? 'Rejecting…' : count === 1 ? 'Reject' : `Reject ${count}`}
          </button>
        </div>
      </div>
    </dialog>
  )
}
