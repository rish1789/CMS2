import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { loadStaffSession } from '../features/staff-login/token'
import { loadPatientSession } from '../features/patient-account/token'
import { loadSuperAdminSession } from '../features/super-admin/token'

// _diagnostics [HIGH] - [APP_SHELL] - [NO_ROUTING_INFRASTRUCTURE]: role/session guards for the
// new app shell - every clinic-scoped/staff-only route previously had no way to be reached by a
// real user at all, since no router of any kind existed anywhere in the dependency tree.
export function RequireStaffSession() {
  const location = useLocation()
  const session = loadStaffSession()
  if (!session) {
    return <Navigate to="/staff/login" state={{ from: location }} replace />
  }
  return <Outlet />
}

export function RequirePatientSession() {
  const location = useLocation()
  const session = loadPatientSession()
  if (!session) {
    return <Navigate to="/patient/login" state={{ from: location }} replace />
  }
  return <Outlet />
}

// 040-super-admin-rbac-login: protects /super-admin-console. A valid staff session does
// NOT satisfy this guard - Super Admin and staff are structurally distinct sessions even
// though both now originate from the same Clinic Portal login screen.
export function RequireSuperAdminSession() {
  const location = useLocation()
  const session = loadSuperAdminSession()
  if (!session) {
    return <Navigate to="/staff/login" state={{ from: location }} replace />
  }
  return <Outlet />
}
