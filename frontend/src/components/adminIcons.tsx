import type { ReactNode } from 'react'

// super-admin-console-redesign polish-2026-09-11: extracted from AdminDashboard so the three
// pages it links to (PendingClinicsList, PendingDoctorsList, TriggerSessionGeneration) can
// reuse the exact same icon per concept in their own page header - navigating in from a
// dashboard tile should land on a page that visually continues it, not a differently-dressed
// page. Same stroke convention as HomePage.tsx's role-selection icons (24x24 viewBox, 20x20
// display, strokeWidth 2, round caps/joins) - one icon vocabulary across the whole app.
interface IconProps {
  className?: string
}

export function ClinicIcon({ className }: IconProps) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M4 9.5 12 4l8 5.5" />
      <rect x="5" y="9.5" width="14" height="10.5" rx="1" />
      <path d="M9.5 20v-5h5v5" />
    </svg>
  )
}

export function DoctorIcon({ className }: IconProps) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <rect x="3.5" y="5" width="17" height="14" rx="2" />
      <circle cx="9.5" cy="11" r="1.75" />
      <path d="M6.5 16c0-1.66 1.34-2.75 3-2.75s3 1.09 3 2.75" />
      <path d="M14.5 9.5h3M14.5 13h3" />
    </svg>
  )
}

export function SessionIcon({ className }: IconProps) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M20 11a8 8 0 0 0-14.9-4M4 3v4h4" />
      <path d="M4 13a8 8 0 0 0 14.9 4M20 21v-4h-4" />
    </svg>
  )
}

export function ArrowIcon({ className }: IconProps) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M3.5 8h9M8.5 4l4 4-4 4" />
    </svg>
  )
}

export function CheckIcon({ className }: IconProps) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  )
}

interface IconBadgeProps {
  children: ReactNode
  className?: string
}

export function IconBadge({ children, className }: IconBadgeProps) {
  return (
    <span
      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 ${className ?? ''}`}
    >
      {children}
    </span>
  )
}
