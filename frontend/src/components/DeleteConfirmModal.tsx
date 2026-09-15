import { useEffect, useRef, useState, type MouseEvent } from 'react'

export interface DeleteConfirmModalProps {
  /** One row for a single-item delete, several for a bulk delete - the modal's copy and confirm phrase adapt to the count. */
  items: { id: string; label: string }[]
  /** e.g. "clinic" / "doctor" - used in the modal's copy. */
  entityNoun: string
  onClose: () => void
  onSubmit: () => Promise<void>
}

// super-admin-console-redesign-2026-09-11: permanent delete is irreversible (unlike Reject,
// which just moves a record to a bin) - one step more friction than RejectConfirmModal's
// reason-dropdown: the admin must type the exact name (single item) or the word DELETE (bulk)
// before the button enables. Uses a native <dialog> (showModal()) shared with
// EmployeeModal/RejectConfirmModal - the browser handles focus trapping and Esc-to-close, so no
// hand-rolled Tab-cycling is needed.
export function DeleteConfirmModal({ items, entityNoun, onClose, onSubmit }: DeleteConfirmModalProps) {
  const [confirmText, setConfirmText] = useState('')
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

  const count = items.length
  const requiredText = count === 1 ? items[0].label : 'DELETE'
  const isConfirmed = confirmText.trim().toLowerCase() === requiredText.trim().toLowerCase()
  const title = count === 1 ? `Permanently delete ${items[0].label}?` : `Permanently delete ${count} ${entityNoun}s?`

  async function handleSubmit() {
    if (!isConfirmed) return
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

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

        <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          This cannot be undone. {count === 1 ? 'The record' : `All ${count} records`} will be permanently removed.
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
          <label htmlFor="delete-confirm-text" className="block text-sm font-medium text-gray-700">
            Type <span className="font-mono font-semibold">{requiredText}</span> to confirm
          </label>
          <input
            id="delete-confirm-text"
            type="text"
            value={confirmText}
            onChange={(event) => setConfirmText(event.target.value)}
            disabled={submitting}
            autoComplete="off"
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
            disabled={!isConfirmed || submitting}
            className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {submitting ? 'Deleting…' : count === 1 ? 'Delete permanently' : `Delete ${count} permanently`}
          </button>
        </div>
      </div>
    </dialog>
  )
}
