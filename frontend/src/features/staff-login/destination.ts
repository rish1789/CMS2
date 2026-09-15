// 040-super-admin-rbac-login: the one shared decision point (FR-011) for where a
// successful Clinic Portal login goes next. The Patient Portal's own redirect is
// separate and does not call this - see research.md R5.

export type ClinicPortalRole = 'STAFF' | 'SUPER_ADMIN'

export function decideClinicPortalDestination(role: ClinicPortalRole): string {
  return role === 'SUPER_ADMIN' ? '/super-admin-console' : '/staff'
}
