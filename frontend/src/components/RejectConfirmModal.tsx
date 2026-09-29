import { useRef, useState } from 'react'
import { Modal, type ModalHandle } from './Modal'
import { ModalHeader } from './ModalHeader'
import { useToast } from './Toast'
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
// single-row and bulk-selection Reject actions, extended with a list of exactly what's affected
// and an optional free-text detail alongside the required structured reason.
// 049-shared-ui-components: migrated onto the shared Modal shell and wired to show a success toast.
export function RejectConfirmModal({ items, entityNoun, onClose, onSubmit }: RejectConfirmModalProps) {
  const [reason, setReason] = useState<RejectionReason | ''>('')
  const [detail, setDetail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const modalRef = useRef<ModalHandle>(null)
  const { showToast } = useToast()

  const count = items.length
  const title = count === 1 ? `Reject ${items[0].label}?` : `Reject ${count} ${entityNoun}s?`

  async function handleSubmit() {
    if (!reason) return
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit(reason, detail.trim())
      showToast(count === 1 ? `${items[0].label} rejected.` : `${count} ${entityNoun}s rejected.`, 'success')
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
            onClick={() => modalRef.current?.close()}
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
    </Modal>
  )
}
