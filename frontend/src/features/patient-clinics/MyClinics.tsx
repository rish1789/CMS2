import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyClinics, type PatientClinic } from './api'
import { loadPatientSession } from '../patient-account/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { PaginationControls } from '../../components/PaginationControls'
import { ClinicIcon, IconBadge } from '../../components/adminIcons'

const CLINICS_PAGE_SIZE = 20

// patient-booking-flow-rebuild: replaces the dashboard's "type a Clinic ID" entry point -
// mirrors the staff-side MyClinicsList's real server-side-paginated browse/pick shape.
export function MyClinics() {
  const [session] = useState(() => loadPatientSession())
  const [clinics, setClinics] = useState<PatientClinic[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!session) return
    setClinics(null)
    listMyClinics(session.token, { page, size: CLINICS_PAGE_SIZE })
      .then((result) => {
        setClinics(result.clinics)
        setTotalCount(result.totalCount)
      })
      .catch(() => setError('Failed to load your clinics.'))
  }, [page, session])

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in to view your clinics.</p>
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Your clinics</h1>
        <p className="mt-0.5 text-sm text-gray-600">Clinics you've visited before. Pick one to book or join a waitlist.</p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {clinics === null && !error && <ListSkeleton rows={3} />}

      {clinics && clinics.length === 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          <p>You haven't visited a clinic yet.</p>
          <Link
            to="/discover"
            className="mt-2 inline-block font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline"
          >
            Find a doctor
          </Link>
        </div>
      )}

      {clinics && clinics.length > 0 && (
        <>
          <ul className="grid gap-3 sm:grid-cols-2">
            {clinics.map((clinic) => (
              <li key={clinic.clinicId}>
                <Link
                  to={`/patient/clinics/${clinic.clinicId}`}
                  className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                >
                  <IconBadge>
                    <ClinicIcon />
                  </IconBadge>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900">{clinic.name}</p>
                    <p className="truncate text-sm text-gray-600">{clinic.address}</p>
                  </div>
                  <span aria-hidden="true" className="shrink-0 text-gray-400">
                    →
                  </span>
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
