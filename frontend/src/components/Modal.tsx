import { forwardRef, useEffect, useImperativeHandle, useRef, type MouseEvent, type ReactNode } from 'react'

export interface ModalHandle {
  close: () => void
}

export interface ModalProps {
  onClose: () => void
  ariaLabel: string
  className?: string
  children: ReactNode
}

// 049-shared-ui-components: a headless native <dialog> shell (showModal(), backdrop-click-to-
// close, focus trapping and Escape-to-close for free from the browser) - deliberately no forced
// header/body/footer layout. DeleteConfirmModal/RejectConfirmModal/EmployeeModal each render
// their own content shape into `children`; only the dialog mechanics they all duplicated
// identically are shared here (research.md Decision 1). Exposes an imperative `close()` via ref
// so a caller's own Cancel/X buttons (rendered as children) can close the dialog, mirroring how
// each of the 3 original implementations called `dialogRef.current?.close()` directly.
export const Modal = forwardRef<ModalHandle, ModalProps>(function Modal({ onClose, ariaLabel, className, children }, ref) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useImperativeHandle(ref, () => ({
    close: () => dialogRef.current?.close(),
  }));

  useEffect(() => {
    dialogRef.current?.showModal()
  }, [])

  function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) {
      dialogRef.current?.close()
    }
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={handleBackdropClick}
      aria-label={ariaLabel}
      className={
        className ??
        'm-auto w-full max-w-md overflow-hidden rounded-xl border-0 bg-white p-0 shadow-xl backdrop:bg-gray-900/50'
      }
    >
      {children}
    </dialog>
  )
})
