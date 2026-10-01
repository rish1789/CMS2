import type { StaffRole } from '../../components/RoleBadge'

// 073-role-aware-clinic-tools: the one table the sidebar, the dashboard tiles and the page
// entry points all read, mirroring the rules the backend already enforces (it stays the
// enforcement boundary - this only stops the UI offering what the server will refuse).
export const CLINIC_TOOL_ROLES = {
  // StaffOnboardingService
  onboard: ['ClinicAdmin'],
  // ClinicProtectionFlagService and ClinicBookingLimitOverrideController
  protection: ['ClinicAdmin'],
  // FrontDeskWalkInService
  walkIn: ['ClinicAdmin', 'Operations'],
} as const satisfies Record<string, readonly StaffRole[]>

export type ClinicRolesStatus = 'loading' | 'ready' | 'failed'

export function hasAnyRole(roles: readonly StaffRole[], allowed?: readonly StaffRole[]): boolean {
  return !allowed || allowed.some((role) => roles.includes(role))
}

const PRIORITY: StaffRole[] = ['ClinicAdmin', 'Doctor', 'Operations']

// For consumers that still read a single role: the highest one held, so a multi-role user gets
// the same answer whatever order the membership rows arrive in.
export function primaryRole(roles: readonly StaffRole[]): StaffRole | undefined {
  return PRIORITY.find((role) => roles.includes(role))
}
