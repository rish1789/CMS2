import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyClinics, type ClinicMembership } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { RoleBadge } from '../../components/RoleBadge'
import { PaginationControls } from '../../components/PaginationControls'
import { ClinicIcon, IconBadge } from '../../components/adminIcons'

const CLINICS_PAGE_SIZE = 20

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// clinic membership, reveal more client-side" pattern.
export function MyClinicsList() {
  const [clinics, setClinics] = useState<ClinicMembership[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session) return
    setClinics(null)
    listMyClinics(session.token, { page, size: CLINICS_PAGE_SIZE })
      .then((result) => {
        setClinics(result.clinics)
        setTotalCount(result.totalCount)
      })
      .catch(() => setError('Failed to load your clinics.'))
  }, [page])

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Your clinics</h1>
        <p className="mt-0.5 text-sm text-gray-600">Clinics where you hold an active role. Pick one to continue.</p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {clinics === null && !error && <ListSkeleton rows={3} />}

      {clinics && clinics.length === 0 && (
        <p className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          You don't have an active role at any clinic yet.
        </p>
      )}

      {clinics && clinics.length > 0 && (
        <>
          <ul className="grid gap-3 sm:grid-cols-2">
            {clinics.map((clinic) => (
              <li key={clinic.clinicId}>
                <Link
                  to={`/staff/clinics/${clinic.clinicId}`}
                  className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                >
                  <IconBadge>
                    <ClinicIcon />
                  </IconBadge>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900">{clinic.name}</p>
                    <p className="truncate text-sm text-gray-600">{clinic.address}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <RoleBadge role={clinic.role} />
                    <span aria-hidden="true" className="text-gray-400">
                      →
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <PaginationControls page={page} pageSize={CLINICS_PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} itemLabel="clinics" />
        </>
      )}
    </div>
  )
}
