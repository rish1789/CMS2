// staff-console polish: same stroke convention as adminIcons.tsx / HomePage.tsx (24x24
// viewBox, 20x20 display, strokeWidth 2, round caps/joins) - one icon vocabulary across the
// whole app, just the staff-tool-specific set. Reuses IconBadge from adminIcons.tsx rather
// than duplicating it.
interface IconProps {
  className?: string
}

export function DaySheetIcon({ className }: IconProps) {
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
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <path d="M3.5 9.5h17M8 3.5v3M16 3.5v3" />
      <path d="M8 13.5h3M8 16.5h5" />
    </svg>
  )
}

export function StethoscopeIcon({ className }: IconProps) {
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
      <path d="M7 4v4a3 3 0 0 0 6 0V4" />
      <path d="M13 10v2a5 5 0 0 0 5 5" />
      <circle cx="18.5" cy="17.5" r="1.75" />
    </svg>
  )
}

export function TeamIcon({ className }: IconProps) {
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
      <circle cx="9" cy="8" r="3" />
      <path d="M3.5 20c0-3.5 2.5-5.5 5.5-5.5s5.5 2 5.5 5.5" />
      <circle cx="17" cy="8" r="2.25" />
      <path d="M14.5 14.8c2.7.3 4.5 2.2 4.5 5.2" />
    </svg>
  )
}

export function SearchIcon({ className }: IconProps) {
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
      <circle cx="10" cy="10" r="6" />
      <path d="M14.5 14.5 20 20" />
    </svg>
  )
}

export function UserPlusIcon({ className }: IconProps) {
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
      <circle cx="9" cy="8" r="3.5" />
      <path d="M3 20c0-3.6 2.7-5.8 6-5.8s6 2.2 6 5.8" />
      <path d="M18 8v5M15.5 10.5h5" />
    </svg>
  )
}

export function ClockIcon({ className }: IconProps) {
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
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3.2 2" />
    </svg>
  )
}

export function InboxIcon({ className }: IconProps) {
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
      <path d="M4 12h4l1.5 3h5L16 12h4" />
      <rect x="4" y="5" width="16" height="14" rx="2" />
    </svg>
  )
}
