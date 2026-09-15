// patient-booking-flow-rebuild: same stroke convention as adminIcons.tsx/staffIcons.tsx (24x24
// viewBox, 20x20 display, strokeWidth 2, round caps/joins) - the patient-dashboard-specific icon
// this app's existing sets don't already cover. Reuses IconBadge/ClinicIcon (adminIcons.tsx) and
// SearchIcon/ClockIcon (staffIcons.tsx) rather than duplicating them.
interface IconProps {
  className?: string
}

export function BookingIcon({ className }: IconProps) {
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
      <path d="m9 14 2 2 4-4" />
    </svg>
  )
}
