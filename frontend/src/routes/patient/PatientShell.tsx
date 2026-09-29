import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { deriveDisplayNameFromEmail, loadPatientSession, storePatientSession } from '../../features/patient-account/token'

// 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): brand mark/wordmark/header
// padding now match BrandHeader.tsx's treatment (same brand text, same py-5/px-6 sm:px-8)
// for consistency with the landing/login pages - not extracted into BrandHeader itself since
// this shell's right side (avatar/email/sign-out) is meaningfully different from that
// logged-out component's single nav link, and this shell already owns its own session state.
export function PatientShell() {
  const [session] = useState(() => loadPatientSession())
  const navigate = useNavigate()

  function signOut() {
    storePatientSession(null)
    navigate('/patient/login', { replace: true })
  }

  const displayName = session ? deriveDisplayNameFromEmail(session.email) : ''

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white px-6 py-5 sm:px-8">
        <div className="mx-auto flex max-w-6xl items-center justify-between">
          <Link to="/patient" className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
              C
            </span>
            CMS2 Clinic Management
          </Link>
          {session && (
            <div className="flex items-center gap-4">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-cobalt-100 text-xs font-semibold text-cobalt-700">
                {displayName.charAt(0).toUpperCase()}
              </span>
              <span className="hidden text-sm text-gray-600 sm:inline">{session.email}</span>
              <button
                type="button"
                onClick={signOut}
                className="rounded-lg border border-gray-300 px-3.5 py-2 text-sm font-medium text-gray-600 transition-colors duration-150 hover:border-gray-400 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-5xl p-6 sm:p-8">
        <Outlet />
      </main>
    </div>
  )
}
