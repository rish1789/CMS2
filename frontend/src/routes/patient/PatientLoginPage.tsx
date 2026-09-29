import { useNavigate } from 'react-router-dom'
import { LoginForm } from '../../features/patient-account/LoginForm'
import { BrandHeader } from '../../components/BrandHeader'
import { BrandFooter } from '../../components/BrandFooter'

// 056-design-copy-quality-pass (login-page rebuild): uses the same BrandHeader/BrandFooter as
// HomePage.tsx (not PublicHeader) so this screen matches the landing page's brand chrome.
// main's own py-6 (was py-12) is this page's slice of the 2026-09-16 fit-without-scrolling
// pass - kept local to this file rather than shrinking BrandHeader/BrandFooter's own padding,
// since those are shared with HomePage.tsx, which wasn't reported as having a scroll problem.
export function PatientLoginPage() {
  const navigate = useNavigate()
  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <BrandHeader />
      <main className="flex flex-1 items-center justify-center px-6 py-6">
        <LoginForm onSuccess={() => navigate('/patient', { replace: true })} />
      </main>
      <BrandFooter />
    </div>
  )
}
