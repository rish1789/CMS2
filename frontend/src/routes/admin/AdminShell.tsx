import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { loadSuperAdminSession, storeSuperAdminSession } from '../../features/super-admin/token'
import { SidebarDrawer } from '../../components/SidebarDrawer'
import { Sidebar, type SidebarNavItem } from '../../components/Sidebar'
import { ClinicIcon, DoctorIcon, HomeIcon, SessionIcon } from '../../components/adminIcons'

// 050-sidebar-navigation T006: the persistent admin sidebar's 4 destinations, all verified
// real routes from App.tsx. No role filtering - AdminShell has exactly one role (Super Admin).
const ADMIN_NAV_ITEMS: SidebarNavItem[] = [
  { to: '/super-admin-console', label: 'Admin home', icon: <HomeIcon />, end: true },
  { to: '/super-admin-console/clinics', label: 'Pending clinic verifications', icon: <ClinicIcon /> },
  { to: '/super-admin-console/doctors', label: 'Pending doctor verifications', icon: <DoctorIcon /> },
  { to: '/super-admin-console/sessions/generate', label: 'Trigger session generation', icon: <SessionIcon /> },
]

// 040-super-admin-rbac-login: Super Admin now authenticates once at the Clinic Portal
// (/staff/login) and gets a stored bearer session, guarded by RequireSuperAdminSession
// in App.tsx - this shell itself stays navigation-only, no guard logic of its own.
//
// super-admin-console-redesign-2026-09-11: chrome now mirrors StaffShell exactly (avatar,
// signed-in identity, real Sign out) instead of a static "SUPER ADMIN" badge with no way to
// sign out short of clearing storage by hand. Section navigation moved out of this header
// into AdminDashboard's tile grid + AdminSectionShell's breadcrumb, same split as
// StaffShell/StaffDashboard/ClinicShell.
export function AdminShell() {
  const [session] = useState(() => loadSuperAdminSession())
  const navigate = useNavigate()

  function signOut() {
    storeSuperAdminSession(null)
    navigate('/staff/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <Link
          to="/super-admin-console"
          className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900 transition-colors hover:text-indigo-600"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
            C
          </span>
          CMS2 Clinic Management
        </Link>
        {session && (
          <div className="flex items-center gap-3 text-sm text-gray-600">
            <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold tracking-wide text-gray-600 uppercase">
              Super Admin
            </span>
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-cobalt-100 text-xs font-semibold text-cobalt-700">
              {session.username.charAt(0).toUpperCase()}
            </span>
            <span className="hidden sm:inline">{session.username}</span>
            <button
              type="button"
              onClick={signOut}
              className="rounded-lg px-2.5 py-1.5 font-medium text-gray-600 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
            >
              Sign out
            </button>
          </div>
        )}
      </header>
      <div className="mx-auto flex max-w-6xl gap-6 p-6 sm:p-8">
        <SidebarDrawer>
          <Sidebar items={ADMIN_NAV_ITEMS} />
        </SidebarDrawer>
        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
