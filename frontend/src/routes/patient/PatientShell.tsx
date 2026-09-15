import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { loadPatientSession, storePatientSession } from '../../features/patient-account/token'

export function PatientShell() {
  const [session] = useState(() => loadPatientSession())
  const navigate = useNavigate()

  function signOut() {
    storePatientSession(null)
    navigate('/patient/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <Link
          to="/patient"
          className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900 transition-colors hover:text-indigo-600"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
            C
          </span>
          Patient portal
        </Link>
        {session && (
          <div className="flex items-center gap-3 text-sm text-gray-600">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-cobalt-100 text-xs font-semibold text-cobalt-700">
              {session.email.charAt(0).toUpperCase()}
            </span>
            <span className="hidden sm:inline">{session.email}</span>
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
      <main className="mx-auto max-w-5xl p-6 sm:p-8">
        <Outlet />
      </main>
    </div>
  )
}
