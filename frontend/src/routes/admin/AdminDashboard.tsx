import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { loadSuperAdminSession } from '../../features/super-admin/token'
import { listClinics } from '../../features/clinic-verification/api'
import { listDoctors } from '../../features/doctor-verification/api'
import { ArrowIcon, ClinicIcon, DoctorIcon, IconBadge, SessionIcon } from '../../components/adminIcons'

// super-admin-console-redesign-2026-09-11: mirrors ClinicToolsDashboard's tile-grid pattern -
// the console previously had no home of its own, its index route just dumped straight into
// Clinic verification with a flat underlined-text nav bar and zero live data.
//
// polish-2026-09-11: brings the icon-badge tile convention already proven on HomePage.tsx's
// role-selection cards (40px rounded-lg bg-indigo-50/text-indigo-600 badge over heading +
// description) to this page, and splits the two live-count queues from the one-off action
// tool into their own groups - the old flat 2-col grid left "Trigger session generation"
// stranded alone in a half-empty row, and gave a browsable queue the same card shape as a
// single manual action, which read as three identical cards for three different kinds of thing.
// Icons live in components/adminIcons.tsx so the pages each tile links to can reuse the exact
// same icon in their own header (see PendingClinicsList/PendingDoctorsList/TriggerSessionGeneration).

function CountBadge({ count }: { count: number | null }) {
  if (count === null) {
    return <span aria-hidden="true" className="h-5 w-16 animate-pulse rounded-full bg-gray-100" />
  }
  if (count === 0) return null
  return (
    <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800">
      {count} pending
    </span>
  )
}

interface QueueTileProps {
  to: string
  icon: ReactNode
  title: string
  description: string
  count: number | null
}

function QueueTile({ to, icon, title, description, count }: QueueTileProps) {
  return (
    <Link
      to={to}
      className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
    >
      <div className="flex items-start justify-between gap-2">
        <IconBadge>{icon}</IconBadge>
        <CountBadge count={count} />
      </div>
      <div>
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
        <p className="mt-1 text-sm text-gray-600">{description}</p>
      </div>
    </Link>
  )
}

export function AdminDashboard() {
  const [pendingClinicCount, setPendingClinicCount] = useState<number | null>(null)
  const [pendingDoctorCount, setPendingDoctorCount] = useState<number | null>(null)

  useEffect(() => {
    const session = loadSuperAdminSession()
    if (!session) return

    // Each fetched independently, same as ClinicToolsDashboard - one failing (or a slow admin
    // endpoint) never blocks the other tile's badge, just leaves its own loading pulse.
    listClinics('PENDING', session.token, { page: 0, size: 1 })
      .then((result) => setPendingClinicCount(result.totalCount))
      .catch(() => {})

    listDoctors('PENDING', session.token, { page: 0, size: 1 })
      .then((result) => setPendingDoctorCount(result.totalCount))
      .catch(() => {})
  }, [])

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Super Admin console</h1>
        <p className="mt-1 text-sm text-gray-600">Platform-wide verification and operational controls.</p>
      </div>

      <div className="space-y-3">
        <h2 className="text-xs font-semibold tracking-wide text-gray-500 uppercase">Verification queues</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <QueueTile
            to="/super-admin-console/clinics"
            icon={<ClinicIcon />}
            title="Clinic verification"
            description="Verify a newly registered clinic, or un-verify one already on the platform."
            count={pendingClinicCount}
          />
          <QueueTile
            to="/super-admin-console/doctors"
            icon={<DoctorIcon />}
            title="Doctor verification"
            description="Review and verify a doctor's medical license before they appear in public discovery."
            count={pendingDoctorCount}
          />
        </div>
      </div>

      <div className="space-y-3">
        <h2 className="text-xs font-semibold tracking-wide text-gray-500 uppercase">Platform tools</h2>
        <Link
          to="/super-admin-console/sessions/generate"
          className="group flex items-center gap-4 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          <IconBadge>
            <SessionIcon />
          </IconBadge>
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-semibold text-gray-900">Trigger session generation</h3>
            <p className="mt-0.5 text-sm text-gray-600">
              Manually re-run nightly session generation for today's 15-day rolling horizon.
            </p>
          </div>
          <ArrowIcon className="shrink-0 text-gray-300 transition-transform duration-150 ease-out group-hover:translate-x-0.5 group-hover:text-indigo-500" />
        </Link>
      </div>
    </div>
  )
}
