import { useEffect, useState } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { listMyClinics } from '../../features/staff-clinics/api'
import { loadStaffSession } from '../../features/staff-login/token'
import { SidebarDrawer } from '../../components/SidebarDrawer'
import { Sidebar, type SidebarNavItem } from '../../components/Sidebar'
import { HomeIcon } from '../../components/adminIcons'
import {
  DaySheetIcon,
  StethoscopeIcon,
  TeamIcon,
  SearchIcon,
  UserPlusIcon,
  ClockIcon,
  InboxIcon,
  ShieldIcon,
  WalkInIcon,
} from '../../components/staffIcons'
import type { StaffRole } from '../../components/RoleBadge'
import { CLINIC_TOOL_ROLES, primaryRole, type ClinicRolesStatus } from './clinicRoles'

// A generously large page size, not "no limit" - listMyClinics is a real paginated endpoint
// (pagination-unification-2026-09-10), but this breadcrumb lookup wants every clinic membership
// in one response. Large enough that no real staff account's clinic memberships exceed it.
const ALL_MEMBERSHIPS_PAGE_SIZE = 200

// _diagnostics [HIGH] - [CLINIC_SHELL] - [OPAQUE_ID]: the breadcrumb used to print the raw
// clinicId UUID (e.g. "13d0c877-7829-4dc1-baa9-0150f855c1bf") - not something a human staff
// member can recognize or use to confirm they're in the right clinic. Resolves it against the
// caller's own clinic memberships (already fetched by MyClinicsList moments earlier, and cheap
// - a staff account is rarely active at more than a handful of clinics) and falls back to the
// id only if that lookup fails (e.g. a stale/invalid clinicId in the URL).
// 050-sidebar-navigation T005/T009: the persistent staff sidebar's 8 destinations, all
// `:clinicId`-scoped (verified real routes from App.tsx) - this is why the sidebar lives here,
// not in StaffShell (which also wraps the clinic-agnostic /staff picker page). "Onboard staff"
// carries the one real, backend-verified role restriction (StaffOnboardingService/
// StaffDeactivationService: ClinicAdmin-only) - every other item is visible to all 3 roles.
function buildNavItems(clinicId: string): SidebarNavItem[] {
  return [
    { to: `/staff/clinics/${clinicId}`, label: 'Clinic tools home', icon: <HomeIcon />, end: true },
    { to: `/staff/clinics/${clinicId}/day-sheet`, label: 'Day sheet', icon: <DaySheetIcon /> },
    // 063-front-desk-walk-in: the front-desk registration screen - the roles that register walk-ins.
    {
      to: `/staff/clinics/${clinicId}/walk-in`,
      label: 'Walk-in',
      icon: <WalkInIcon />,
      roles: CLINIC_TOOL_ROLES.walkIn,
    },
    { to: `/staff/clinics/${clinicId}/doctors`, label: 'Doctors', icon: <StethoscopeIcon /> },
    { to: `/staff/clinics/${clinicId}/staff`, label: 'Staff', icon: <TeamIcon /> },
    { to: `/staff/clinics/${clinicId}/patients/search`, label: 'Find a patient', icon: <SearchIcon /> },
    {
      to: `/staff/clinics/${clinicId}/onboard`,
      label: 'Onboard staff',
      icon: <UserPlusIcon />,
      roles: CLINIC_TOOL_ROLES.onboard,
    },
    { to: `/staff/clinics/${clinicId}/inbox`, label: 'Inbox', icon: <InboxIcon /> },
    { to: `/staff/clinics/${clinicId}/waitlist/join`, label: 'Join waitlist', icon: <ClockIcon /> },
    {
      to: `/staff/clinics/${clinicId}/protection`,
      label: 'Booking protection',
      icon: <ShieldIcon />,
      roles: CLINIC_TOOL_ROLES.protection,
    },
  ]
}

export function ClinicShell() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [clinicName, setClinicName] = useState<string | null>(null)
  // Only worth offering "Switch clinic" once the caller actually has somewhere else to
  // switch to - defaults to true (shown) until the fetch below resolves, since hiding it
  // pre-emptively would flash for the common multi-clinic case.
  const [hasMultipleClinics, setHasMultipleClinics] = useState(true)
  // 073-role-aware-clinic-tools: every role at this clinic (one membership row per role), keyed
  // by the clinic it was resolved for - a different clinic in the URL reads as still loading
  // until its own lookup finishes, so one clinic's rights never show at another.
  const [resolvedRoles, setResolvedRoles] = useState<{
    clinicId: string
    roles: StaffRole[]
    status: Exclude<ClinicRolesStatus, 'loading'>
  } | null>(null)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session || !clinicId) return
    let cancelled = false
    listMyClinics(session.token, { size: ALL_MEMBERSHIPS_PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        const atThisClinic = result.clinics.filter((c) => c.clinicId === clinicId)
        setClinicName(atThisClinic[0]?.name ?? null)
        setResolvedRoles({ clinicId, roles: atThisClinic.map((c) => c.role), status: 'ready' })
        setHasMultipleClinics(result.totalCount > 1)
      })
      .catch(() => {
        // The id fallback below still keeps the breadcrumb usable; restricted tools stay closed.
        if (!cancelled) setResolvedRoles({ clinicId, roles: [], status: 'failed' })
      })
    return () => {
      cancelled = true
    }
  }, [clinicId])

  const current = resolvedRoles?.clinicId === clinicId ? resolvedRoles : null
  const roles = current?.roles ?? []
  const rolesStatus: ClinicRolesStatus = current?.status ?? 'loading'
  const role = primaryRole(roles)

  return (
    <div className="flex gap-6">
      {/* 056-design-copy-quality-pass: sticky (not fixed - StaffShell's header must stay
          in normal flow above it) only from sm: up, so mobile's hamburger-drawer behavior
          (SidebarDrawer's own sm:hidden branch) is completely untouched below that breakpoint
          (research.md Decision 1 & 3). self-start stops the flex row's default stretch from
          forcing this column to match the (often much taller) content column's height. */}
      <div className="sm:sticky sm:top-0 sm:self-start">
        <SidebarDrawer>
          <Sidebar items={buildNavItems(clinicId ?? '')} activeRoles={roles} />
        </SidebarDrawer>
      </div>
      {/* max-w-4xl per DESIGN.md's documented content-area convention, applied once here -
          not centered (no mx-auto), so it hugs the sidebar directly instead of floating in
          the middle of the remaining space (research.md Decision 2). */}
      <div className="min-w-0 max-w-4xl flex-1 space-y-6 px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Link
              to={`/staff/clinics/${clinicId}`}
              className="font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600"
            >
              Clinic dashboard
            </Link>
            <span aria-hidden="true">/</span>
            <span className="font-semibold text-gray-900">{clinicName ?? clinicId}</span>
          </div>
          {hasMultipleClinics && (
            <Link
              to="/staff"
              className="rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-600 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
            >
              Switch clinic
            </Link>
          )}
        </div>
        {/* 057-day-sheet-status-overhaul: first per-page role plumbing in this codebase -
            role was already resolved above for the sidebar's own activeRole filtering, but
            never reached routed child pages until now (research.md Decision 6). */}
        <Outlet context={{ role, roles, rolesStatus } satisfies ClinicShellOutletContext} />
      </div>
    </div>
  )
}

export interface ClinicShellOutletContext {
  /** The highest role held here (ClinicAdmin, then Doctor, then Operations) - kept for single-role consumers. */
  role: StaffRole | undefined
  /** 073-role-aware-clinic-tools: every role held at this clinic. */
  roles: StaffRole[]
  rolesStatus: ClinicRolesStatus
}
