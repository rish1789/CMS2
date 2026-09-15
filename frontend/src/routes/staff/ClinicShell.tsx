import { useEffect, useState } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { listMyClinics } from '../../features/staff-clinics/api'
import { loadStaffSession } from '../../features/staff-login/token'

// A generously large page size, not "no limit" - listMyClinics is a real paginated endpoint
// (pagination-unification-2026-09-10), but this breadcrumb lookup wants every clinic membership
// in one response. Large enough that no real staff account's clinic memberships exceed it.
const ALL_MEMBERSHIPS_PAGE_SIZE = 200

// _diagnostics [HIGH] - [CLINIC_SHELL] - [OPAQUE_ID]: the breadcrumb used to print the raw
// clinicId UUID (e.g. "13d0c877-7829-4dc1-baa9-0150f855c1bf") - not something a human staff
// member can recognize or use to confirm they're in the right clinic. Resolves it against the
// caller's own clinic memberships (already fetched by MyClinicsList moments earlier, and cheap
// - a staff account is rarely active at more than a handful of clinics) and falls back to the
// id only if that lookup fails (e.g. a stale/invalid clinicId in the URL).
export function ClinicShell() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [clinicName, setClinicName] = useState<string | null>(null)
  // Only worth offering "Switch clinic" once the caller actually has somewhere else to
  // switch to - defaults to true (shown) until the fetch below resolves, since hiding it
  // pre-emptively would flash for the common multi-clinic case.
  const [hasMultipleClinics, setHasMultipleClinics] = useState(true)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session || !clinicId) return
    let cancelled = false
    listMyClinics(session.token, { size: ALL_MEMBERSHIPS_PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        setClinicName(result.clinics.find((c) => c.clinicId === clinicId)?.name ?? null)
        setHasMultipleClinics(result.totalCount > 1)
      })
      .catch(() => {
        // Best-effort only - the id fallback below still keeps the breadcrumb usable.
      })
    return () => {
      cancelled = true
    }
  }, [clinicId])

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <Link
            to={`/staff/clinics/${clinicId}`}
            className="font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600"
          >
            Clinic dashboard
          </Link>
          <span aria-hidden="true">/</span>
          <span className="font-semibold text-gray-900">{clinicName ?? clinicId}</span>
        </div>
        {hasMultipleClinics && (
          <Link
            to="/staff"
            className="rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-600 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
          >
            Switch clinic
          </Link>
        )}
      </div>
      <Outlet />
    </div>
  )
}
