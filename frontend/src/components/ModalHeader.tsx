import type { ReactNode } from 'react'

export interface ModalHeaderProps {
  title: ReactNode
  onCloseClick: () => void
  disabled?: boolean
}

// 049-shared-ui-components: the title + close-button markup duplicated identically in
// DeleteConfirmModal/RejectConfirmModal - optional for callers whose layout fits it (EmployeeModal
// does not, and composes its own header instead).
export function ModalHeader({ title, onCloseClick, disabled }: ModalHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-3">
      <h2 className="text-base font-semibold text-gray-900">{title}</h2>
      <button
        type="button"
        onClick={onCloseClick}
        disabled={disabled}
        aria-label="Close"
        className="rounded-md p-1 text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-600 disabled:pointer-events-none disabled:opacity-50"
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="currentColor">
          <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
        </svg>
      </button>
    </div>
  )
}
