import { Modal } from './Modal'
import { ModalHeader } from './ModalHeader'

export interface ResetPasswordResultModalProps {
  email: string
  temporaryPassword: string
  onClose: () => void
  // real-bug-fix 2026-09-17: parametrized so this same modal can announce a reset for any kind
  // of account (a staff member, not only a clinic admin) without duplicating the component.
  recipientLabel?: string
}

// real-bug-fix 2026-09-16: shown exactly once, mirroring OnboardStaffForm's own "hand these
// credentials to the new hire directly - they are shown once and cannot be retrieved again"
// contract - the same is true here, since the password is never stored or logged in plaintext.
export function ResetPasswordResultModal({
  email,
  temporaryPassword,
  onClose,
  recipientLabel = 'clinic admin',
}: ResetPasswordResultModalProps) {
  return (
    <Modal onClose={onClose} ariaLabel="Password reset">
      <div className="space-y-4 p-6">
        <ModalHeader title="Password reset" onCloseClick={onClose} />
        <p className="text-sm text-gray-700">
          Hand this new password to the {recipientLabel} directly — it is shown once and cannot be retrieved again.
        </p>
        <dl className="space-y-2 rounded-md bg-gray-50 p-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Email</dt>
            <dd className="text-gray-900">{email}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">New temporary password</dt>
            <dd className="font-mono text-gray-900">{temporaryPassword}</dd>
          </div>
        </dl>
        <button
          type="button"
          onClick={onClose}
          className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Done
        </button>
      </div>
    </Modal>
  )
}
