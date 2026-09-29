import { useRef, useState } from 'react'
import { Modal, type ModalHandle } from './Modal'
import { ModalHeader } from './ModalHeader'
import { useToast } from './Toast'

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
// before the button enables.
// 049-shared-ui-components: migrated onto the shared Modal shell (native <dialog>, unchanged
// focus-trap/Esc-to-close/backdrop-click behavior) and wired to show a success toast.
export function DeleteConfirmModal({ items, entityNoun, onClose, onSubmit }: DeleteConfirmModalProps) {
  const [confirmText, setConfirmText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const modalRef = useRef<ModalHandle>(null)
  const { showToast } = useToast()

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
      showToast(count === 1 ? `${items[0].label} deleted.` : `${count} ${entityNoun}s deleted.`, 'success')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal ref={modalRef} onClose={onClose} ariaLabel={title}>
      <div className="max-h-[80vh] space-y-4 overflow-y-auto p-5">
        <ModalHeader title={title} onCloseClick={() => modalRef.current?.close()} disabled={submitting} />

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
            onClick={() => modalRef.current?.close()}
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
    </Modal>
  )
}
