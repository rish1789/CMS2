import { useLocation } from 'react-router-dom'
import { SignupForm } from '../../features/patient-account/SignupForm'
import { returnPathFrom } from '../../features/patient-account/returnTo'
import { PublicHeader } from '../PublicHeader'

// 070-login-return-path: the signup route, carrying a validated `?returnTo=` through to the
// success screen's "Log in" link so a patient who signs up mid-journey still lands where they were going.
export function PatientSignupPage() {
  const location = useLocation()
  const returnTo = returnPathFrom(null, location.search)
  return (
    <div className="min-h-screen bg-gray-50">
      <PublicHeader />
      <div className="flex items-center justify-center px-6 py-12">
        <SignupForm returnTo={returnTo} />
      </div>
    </div>
  )
}
