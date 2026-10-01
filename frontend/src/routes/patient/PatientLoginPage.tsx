import { useLocation, useNavigate } from 'react-router-dom'
import { LoginForm } from '../../features/patient-account/LoginForm'
import { returnPathFrom } from '../../features/patient-account/returnTo'
import { BrandHeader } from '../../components/BrandHeader'
import { BrandFooter } from '../../components/BrandFooter'

// 056-design-copy-quality-pass (login-page rebuild): uses the same BrandHeader/BrandFooter as
// HomePage.tsx (not PublicHeader) so this screen matches the landing page's brand chrome.
// main's own py-6 (was py-12) is this page's slice of the 2026-09-16 fit-without-scrolling
// pass - kept local to this file rather than shrinking BrandHeader/BrandFooter's own padding,
// since those are shared with HomePage.tsx, which wasn't reported as having a scroll problem.
//
// 070-login-return-path (live-audit finding 4): after login, go back to where the patient was
// headed (e.g. the clinic and doctor picked in discovery), validated as an internal patient
// path; otherwise the dashboard.
export function PatientLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = returnPathFrom(location.state, location.search)
  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <BrandHeader />
      <main className="flex flex-1 items-center justify-center px-6 py-6">
        <LoginForm returnTo={returnTo} onSuccess={() => navigate(returnTo ?? '/patient', { replace: true })} />
      </main>
      <BrandFooter />
    </div>
  )
}
