export type StaffRole = 'ClinicAdmin' | 'Doctor' | 'Operations'

// Distinguishes the three RoleAssignment roles at a glance across MyClinicsList and
// StaffPicker - amber for the role that carries approval/admin weight, cobalt (the
// identity/status accent, not the primary teal action color) for the clinical role,
// neutral gray for everything else.
const ROLE_BADGE_CLASS: Record<StaffRole, string> = {
  ClinicAdmin: 'bg-amber-100 text-amber-800',
  Doctor: 'bg-cobalt-100 text-cobalt-700',
  Operations: 'bg-gray-100 text-gray-700',
}

export function RoleBadge({ role }: { role: StaffRole }) {
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${ROLE_BADGE_CLASS[role]}`}>
      {role}
    </span>
  )
}
