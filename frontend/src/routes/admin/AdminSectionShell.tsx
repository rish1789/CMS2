import { Link, Outlet } from 'react-router-dom'

// super-admin-console-redesign-2026-09-11: mirrors ClinicShell's breadcrumb-wrapper pattern -
// wraps only the console's sub-pages (clinics/doctors/sessions), not the AdminDashboard home
// itself, exactly like ClinicShell wraps clinic-scoped routes but not StaffDashboard.
export function AdminSectionShell() {
  return (
    <div className="space-y-6">
      <Link
        to="/super-admin-console"
        className="inline-flex items-center gap-1 text-sm font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600"
      >
        <span aria-hidden="true">←</span> Super Admin console
      </Link>
      <Outlet />
    </div>
  )
}
