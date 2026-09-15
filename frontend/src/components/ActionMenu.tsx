import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

// Fallback used only for the very first frame of the very first open, before the portal has
// ever mounted anything to measure - repositionMenu() immediately corrects it against the real
// rendered height once mounted (see the useLayoutEffect below), so this never has to match any
// particular menu's actual item count.
//
// staff-console-audit-2026-09-10 P3: this used to be the only height source (never corrected
// against the real ~140px a 4-item menu renders at), so a menu near the bottom of the viewport
// would decide to open downward, not fit, and run off-screen.
const MENU_HEIGHT_ESTIMATE_PX = 80
const MENU_WIDTH_PX = 176

interface MenuPosition {
  top: number
  left: number
}

/**
 * A small disclosure menu for secondary row actions - native <details>/<summary> needs no extra
 * state and closes itself once the browser navigates away. Used to keep a busy row (e.g. a
 * booked slot with several possible actions) down to a couple of primary links plus one "More"
 * trigger instead of a wall of text links.
 *
 * 042-day-sheet-hardening FR-008/FR-009: `name` groups instances into a native, browser-driven
 * mutually-exclusive set (only one open at a time within the same `name`) - callers sharing one
 * page/list should pass the same value; unrelated instances elsewhere should use a different one.
 * The popup itself is rendered through a portal to `document.body` with `position: fixed`,
 * computed from the trigger's own `getBoundingClientRect()` - the same fix already proven on the
 * Roster page's row-actions kebab, needed because a same-DOM-subtree `absolute`-positioned popup
 * gets clipped by any scrollable ancestor (an `overflow-x` ancestor forces `overflow-y` to `auto`
 * too, per the CSS spec) regardless of which direction it opens.
 */
export function ActionMenu({ label, name, children }: { label: string; name: string; children: ReactNode }) {
  const detailsRef = useRef<HTMLDetailsElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null)
  const isOpen = menuPosition !== null

  function repositionMenu() {
    const trigger = detailsRef.current
    if (!trigger?.open) {
      setMenuPosition(null)
      return
    }
    const rect = trigger.getBoundingClientRect()
    const menuHeight = menuRef.current?.offsetHeight ?? MENU_HEIGHT_ESTIMATE_PX
    const spaceBelow = window.innerHeight - rect.bottom
    const openUpward = spaceBelow < menuHeight
    setMenuPosition({
      top: openUpward ? Math.max(8, rect.top - menuHeight - 4) : rect.bottom + 4,
      left: Math.max(8, rect.right - MENU_WIDTH_PX),
    })
  }

  // Re-measure against the portal's real height once it's actually in the DOM (the open-toggle
  // handler above only has the rough estimate to go on for that first frame), and keep the
  // popup glued to its trigger on scroll/resize instead of leaving it stranded at a stale
  // position - useLayoutEffect runs before paint, so the correction never flickers.
  useLayoutEffect(() => {
    if (!isOpen) return
    repositionMenu()
    window.addEventListener('scroll', repositionMenu, true)
    window.addEventListener('resize', repositionMenu)
    return () => {
      window.removeEventListener('scroll', repositionMenu, true)
      window.removeEventListener('resize', repositionMenu)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen])

  return (
    <details ref={detailsRef} name={name} className="group relative" onToggle={repositionMenu}>
      <summary className="inline-flex cursor-pointer list-none items-center gap-1 rounded-md px-1.5 py-1 text-sm font-medium text-gray-600 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 [&::-webkit-details-marker]:hidden">
        {label}
        <span aria-hidden="true" className="text-xs transition-transform duration-150 group-open:rotate-180">
          ▾
        </span>
      </summary>
      {menuPosition &&
        createPortal(
          <div
            ref={menuRef}
            style={{ position: 'fixed', top: menuPosition.top, left: menuPosition.left, width: MENU_WIDTH_PX }}
            className="z-50 space-y-0.5 rounded-lg border border-gray-200 bg-white p-1.5 shadow-md"
          >
            {children}
          </div>,
          document.body,
        )}
    </details>
  )
}
