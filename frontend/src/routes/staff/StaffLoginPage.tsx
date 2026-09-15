import { useNavigate } from 'react-router-dom'
import { StaffLoginForm } from '../../features/staff-login/StaffLoginForm'
import { decideClinicPortalDestination } from '../../features/staff-login/destination'
import { PublicHeader } from '../PublicHeader'

export function StaffLoginPage() {
  const navigate = useNavigate()
  return (
    <div className="min-h-screen bg-gray-50">
      <PublicHeader />
      <div className="flex items-center justify-center px-6 py-12">
        <StaffLoginForm
          onSuccess={({ role }) => navigate(decideClinicPortalDestination(role), { replace: true })}
        />
      </div>
    </div>
  )
}
