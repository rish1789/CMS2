import type { ReactNode } from 'react'

export type BadgeColor = 'gray' | 'indigo' | 'cobalt' | 'amber' | 'red' | 'green'

const COLOR_CLASS: Record<BadgeColor, string> = {
  gray: 'bg-gray-100 text-gray-700',
  indigo: 'bg-indigo-100 text-indigo-700',
  cobalt: 'bg-cobalt-100 text-cobalt-700',
  amber: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-700',
  green: 'bg-green-100 text-green-800',
}

export interface BadgeProps {
  color?: BadgeColor
  children: ReactNode
}

// 049-shared-ui-components T015 (research.md Decision 4): a small generic pill, distinct
// from RoleBadge (which encodes role-specific logic and stays its own component).
export function Badge({ color = 'gray', children }: BadgeProps) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${COLOR_CLASS[color]}`}>{children}</span>
}
