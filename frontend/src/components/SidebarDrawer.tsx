import { useEffect, useRef, useState, type MouseEvent, type ReactNode } from 'react'
import { Button } from './Button'

export interface SidebarDrawerProps {
  children: ReactNode
}

function HamburgerIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <path d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
    </svg>
  )
}

// 050-sidebar-navigation T002: below `sm:`, the sidebar becomes a native <dialog>-backed,
// left-edge drawer - reuses Part 3's native-dialog pattern (free focus trap + Escape-to-close)
// the same way 046's Modal does, but not the Modal component itself: Modal's `m-auto` centered
// shell is the wrong interaction shape for an edge-anchored slide-in panel (research.md
// Decision 3). The hamburger/close triggers reuse 046's `Button` (FR-004).
export function SidebarDrawer({ children }: SidebarDrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (open) {
      dialogRef.current?.showModal()
    } else {
      dialogRef.current?.close()
    }
  }, [open])

  function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) {
      setOpen(false)
    }
  }

  return (
    <>
      <div className="p-2 sm:hidden">
        <Button variant="secondary" aria-label="Open navigation menu" onClick={() => setOpen(true)}>
          <HamburgerIcon />
        </Button>
      </div>
      <div className="hidden sm:block">{children}</div>
      <dialog
        ref={dialogRef}
        onClose={() => setOpen(false)}
        onClick={handleBackdropClick}
        aria-label="Navigation menu"
        className="fixed inset-y-0 left-0 m-0 h-full w-64 max-w-[80vw] border-0 bg-white p-0 shadow-xl backdrop:bg-gray-900/50 sm:hidden"
      >
        <div className="flex justify-end p-2">
          <Button variant="secondary" aria-label="Close navigation menu" onClick={() => setOpen(false)}>
            <CloseIcon />
          </Button>
        </div>
        <div onClick={() => setOpen(false)}>{children}</div>
      </dialog>
    </>
  )
}
