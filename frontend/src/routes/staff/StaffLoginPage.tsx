import { Link, useNavigate } from 'react-router-dom'
import { StaffLoginForm } from '../../features/staff-login/StaffLoginForm'
import { decideClinicPortalDestination } from '../../features/staff-login/destination'
import { PublicHeader } from '../PublicHeader'

// 056-design-copy-quality-pass (landing-page rebuild): this page is the one "Clinic login"
// entry point linked from HomePage.tsx - Super Admin sign-in already role-routes from here
// (decideClinicPortalDestination), and clinic registration (/register) is now reachable only
// from here, not as its own top-level homepage card.
export function StaffLoginPage() {
  const navigate = useNavigate()
  return (
    <div className="min-h-screen bg-gray-50">
      <PublicHeader />
      <div className="flex flex-col items-center justify-center gap-4 px-6 py-12">
        <StaffLoginForm
          onSuccess={({ role }) => navigate(decideClinicPortalDestination(role), { replace: true })}
        />
        <p className="text-sm text-gray-500">
          New clinic?{' '}
          <Link
            to="/register"
            className="font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
          >
            Register your clinic
          </Link>
        </p>
      </div>
    </div>
  )
}
