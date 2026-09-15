import { Link } from 'react-router-dom'

export function HomePage() {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white px-6 py-4">
        <span className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
            C
          </span>
          CMS2 Clinic Management
        </span>
      </header>

      <main className="mx-auto max-w-3xl px-6 py-14 sm:py-16">
        <div className="max-w-xl">
          <h1 className="text-3xl text-gray-900">One account, every clinic.</h1>
          <p className="mt-3 text-base text-gray-600">
            Book appointments as a patient, run day-to-day operations as clinic staff, or manage
            verification as a Super Admin.
          </p>
        </div>

        <div className="mt-10 grid grid-cols-1 gap-5 sm:grid-cols-2">
          <section className="flex flex-col rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
            <PatientIcon />
            <h2 className="mt-4 text-lg font-semibold text-gray-900">Patients</h2>
            <p className="mt-1 flex-1 text-sm text-gray-600">
              Book appointments, join queues and waitlists, and manage your visits.
            </p>
            <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2">
              <Link
                to="/patient/login"
                className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Log in
              </Link>
              <Link
                to="/patient/signup"
                className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline"
              >
                Sign up
              </Link>
            </div>
            <Link
              to="/discover"
              className="mt-3 text-sm font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600"
            >
              Find a doctor without an account →
            </Link>
          </section>

          <section className="flex flex-col rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
            <StaffIcon />
            <h2 className="mt-4 text-lg font-semibold text-gray-900">Clinic staff</h2>
            <p className="mt-1 flex-1 text-sm text-gray-600">
              Manage schedules, bookings, walk-ins, and the day-to-day of your clinic.
            </p>
            <div className="mt-5">
              <Link
                to="/staff/login"
                className="inline-block rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Staff sign in
              </Link>
            </div>
          </section>
        </div>

        <div className="mt-5 flex flex-col gap-5 sm:flex-row">
          <section className="flex flex-1 items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">Running a clinic?</h2>
              <p className="mt-0.5 text-sm text-gray-600">Register your clinic and admin account.</p>
            </div>
            <Link
              to="/register"
              className="shrink-0 rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
            >
              Register
            </Link>
          </section>
        </div>

        <p className="mt-10 text-sm text-gray-500">
          Super Admin?{' '}
          <Link
            to="/super-admin-console"
            className="font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600 hover:underline"
          >
            Open the admin console
          </Link>
        </p>
      </main>
    </div>
  )
}

function PatientIcon() {
  return (
    <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 21c0-4-3-6-3-10a3 3 0 0 1 6 0c0 4-3 6-3 10Z" />
        <circle cx="12" cy="7" r="1" />
      </svg>
    </span>
  )
}

function StaffIcon() {
  return (
    <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M3 9h18M8 4v0M9 14l2 2 4-4" />
      </svg>
    </span>
  )
}
