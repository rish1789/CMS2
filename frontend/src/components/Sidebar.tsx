import { NavLink } from 'react-router-dom'
import type { ReactNode } from 'react'
import type { StaffRole } from './RoleBadge'

export interface SidebarNavItem {
  to: string
  label: string
  icon: ReactNode
  /** Omit to show to every role. `end` opts into NavLink's exact-match (for a shell's own index/"home" route). */
  roles?: readonly StaffRole[]
  end?: boolean
}

export interface SidebarProps {
  items: SidebarNavItem[]
  /** The signed-in staff member's role at the current clinic - omit when the shell has only one role (Admin). */
  activeRole?: StaffRole
  /** 073-role-aware-clinic-tools: every role held at the current clinic - an item shows when any of them is allowed. */
  activeRoles?: readonly StaffRole[]
}

// 050-sidebar-navigation T001: react-router's own NavLink gives correct active-state matching
// (including exact-vs-prefix via `end`) for free - no hand-rolled useLocation comparison.
export function Sidebar({ items, activeRole, activeRoles }: SidebarProps) {
  const held = activeRoles ?? (activeRole ? [activeRole] : [])
  const visibleItems = items.filter((item) => !item.roles || item.roles.some((role) => held.includes(role)))

  return (
    <nav aria-label="Primary" className="flex w-56 shrink-0 flex-col gap-1 p-3">
      {visibleItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 ${
              isActive ? 'bg-indigo-100 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
            }`
          }
        >
          <span aria-hidden="true" className="shrink-0">
            {item.icon}
          </span>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
